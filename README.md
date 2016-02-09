# bbb-helloworld-federates
Model files and code for the example HVAC + controller with the BBB and associated hardware.

Table of Contents:
------------------
* [model-creation]
* [code-generation]
* [code-development]
* [code-compilation]
* [configure-the-bbb]
* [copying-files]
* [running-the-federates]

## Model Creation

  * Create a new folder `$C2WTROOT/examples/BBBHelloWorld`, this will be `$BBBROOT`
  * Create a new folder `$BBBROOT/models/gme`
  * Copy the HelloWorld XME from `$C2WTROOT/examples/HelloWorld/models/gme/HelloWorld.xme` to `$BBBROOT/models/gme/BBBHelloWorld.xme`
  * Open the new model file with GME and create a new project file for it in the same folder as `BBBHelloWorld.mga`
  * Rename the root object to BBBHelloWorld
  * Delete the `Objects` FOM Sheet
  * Delete the `Federates` FOM Sheet
  * Delete the `Simulation` FOM Sheet
  * In the `Interactions` FOM Sheet:
    * Delete the `Ping` interaction
    * Add a new interaction `ControlMessage`:
        * Add four new `boolean` parameters: `heater, cooler, fan1, fan2`
    * Add a new interaction `SensorMessage`:
        * Add a `double` parameter: `Value`
        * Add a `string` parameter: `Units`
  * Create two new top-level FOM Sheets:
    * `Federates`
      * Create two models of type `Federate` in Federates (insert model): named `Controller`, and `HVAC` with the default properties, this will make them Java federates.
      * Create reference of type `InteractionProxy` in Federates (insert reference) named `ControlMessage` and drag `BBBHelloWorld/Ineractions/ControlMessage` from the GME Browser/ tree navigator onto the new proxy to set the reference.
      * Create reference of type `InteractionProxy` in Federates (insert reference) named `SensorMessage` and drag `BBBHelloWorld/Ineractions/SensorMessage` from the GME Browser/ tree navigator onto the new proxy to set the reference.
      * Enter connect mode (ctrl+2) and connect `Controller` to `ControlMessage`, and `ControlMessage` to `HVAC`
      * Enter connect mode (ctrl+2) and connect `HVAC` to `SensorMessage`, and `SensorMessage` to `Controller`
    * `Simulation`
      * Create `network` in Simulation named `Network`
        * Create `computer` in Network named `localhost`
          * set its IP, username, and password properties as required
        * Create `computer` in Network named `BBB`
          * set its IP, username, and password properties as required (NOTE: the username for the BBB must be **root** because of libbulldog permission requirements)
      * Create `experiment` in Simulation named `main`
        * Create `FederateExecution` references named `Controller` and `HVAC` with references to the Federates created earlier
      * Create `deployment` in Simulation named `main-Deployment`
        * Create `ExperimentRef` reference named `main` with reference to the experiment
        * Create `Host` references named `localhost` and `BBB` with references to the Computers created earlier
          * Set Attribute `Main` to `True` for the Host `localhost`
        * Enter connect mode and connect `main/Controller` to `localhost` and `main/HVAC` to `BBB`

## Code Generation

  * Run the `C2W Main Interpreter` by clicking it's corresponding toolbar button (java icon, upper right)
  * Run the `C2W Deployment Interpreter` by clicking it's corresponding toolbar button (java icon, upper right)

## Code Development

  * Create a new folder `$C2WTROOT/3rdParty/java/libbulldog`
    * place `bulldog.beagleboneblack.hardfp.jar` and `libbulldog-linux.so` into this folder
  * Create a new folder `$BBBROOT/java/BBBHelloWorld`
    * Create new file in this directory `Controller.java`:
    ```java
        package BBBHelloWorld;

        import c2w.hla.InteractionRoot;

        public class Controller extends ControllerBase {

            public Controller( String[] args ) throws Exception {
                super( args );
            }

            private void execute() throws Exception {

                double currentTime = 0;

                AdvanceTimeRequest atr = new AdvanceTimeRequest( currentTime );
                putAdvanceTimeRequest( atr );

                readyToPopulate();
                readyToRun();

                startAdvanceTimeThread();

                InteractionRoot interactionRoot;

                boolean heaterOn = true;
                boolean coolerOn = false;
                boolean fan1On = true;
                boolean fan2On = true;
                double setPoint = 25; // deg C
                double tolerance = 1; // deg C
		
                double value = setPoint;
                double diff = 0;
                String units = "deg C";

                while( true ) {

                    currentTime += 1;
                    atr.requestSyncStart();

                    while (  ( interactionRoot = getNextInteractionNoWait() ) != null ) {
                          SensorMessage msg = (SensorMessage)interactionRoot;
                          value = msg.get_value();
                          units = msg.get_units();
                          diff = value-setPoint;

                          if ( Math.abs(diff) > tolerance && diff > 0 ) {
                             coolerOn = true;
                             heaterOn = false;
                             fan1On = true;
                             fan2On = true;
                          }
                          else if ( Math.abs(diff) > tolerance && diff < 0 ) {
                             coolerOn = false;
                             heaterOn = true;
                             fan1On = false;
                             fan2On = false;
                          }
                    }

                    ControlMessage msg = create_ControlMessage();
                    msg.set_heater( heaterOn );
                    msg.set_cooler( coolerOn );
                    msg.set_fan1( fan1On );
                    msg.set_fan2( fan2On );

                    System.out.println( "Controller: Sending ControlMessage interaction to bring current temp" 
                                        + value + units + " to " + setPoint + "deg C" );
                    msg.sendInteraction( getRTI(), currentTime );

                    AdvanceTimeRequest newATR = new AdvanceTimeRequest( currentTime );
                    putAdvanceTimeRequest( newATR );

                    atr.requestSyncEnd();
                    atr = newATR;
                }

            }
            public static void main( String[] args ) {
                try {
                        Controller controller = new Controller( args );
                    controller.execute();
                } catch ( Exception e ) {
                    System.err.println( "Exception caught: " + e.getMessage() );
                    e.printStackTrace();
                }
            }
        }
    ```
    * Create new file in this directory `HVAC.java`:
    ```java
        package BBBHelloWorld;

        import c2w.hla.InteractionRoot;

        import java.io.IOException;

        import org.bulldog.beagleboneblack.BBBNames;
        import org.bulldog.core.Signal;
        import org.bulldog.core.gpio.DigitalOutput;
        import org.bulldog.core.platform.Board;
        import org.bulldog.core.platform.Platform;
        import org.bulldog.core.util.BulldogUtil;
        import org.bulldog.core.io.bus.i2c.I2cBus;

        import java.io.ByteArrayOutputStream;
        import java.io.InputStream;

        public class HVAC extends HVACBase {

            public HVAC( String[] args ) throws Exception {
                super( args );
            }

            private void execute() throws Exception {

                double currentTime = 0;

                AdvanceTimeRequest atr = new AdvanceTimeRequest( currentTime );
                putAdvanceTimeRequest( atr );

                readyToPopulate();
                readyToRun();

                startAdvanceTimeThread();

                InteractionRoot interactionRoot;

                //Detect the board we are running on
                Board board = Platform.createBoard();
                I2cBus bus = board.getI2cBus(BBBNames.I2C_1);
                int deviceAddress = 0x18;
                //Let's assume we have got a device on address xx
                TemperatureSensor sensor = new TemperatureSensor(bus, deviceAddress);
                //Set up a digital output
                DigitalOutput relay3Fan1 = board.getPin(BBBNames.P9_30).as(DigitalOutput.class);
                relay3Fan1.write(Signal.High);
                DigitalOutput relay4Fan2 = board.getPin(BBBNames.P9_27).as(DigitalOutput.class);
                relay4Fan2.write(Signal.High);
                DigitalOutput relay1Heating = board.getPin(BBBNames.P9_15).as(DigitalOutput.class);
                relay1Heating.write(Signal.High);
                DigitalOutput relay2Cooling = board.getPin(BBBNames.P9_23).as(DigitalOutput.class);
                relay2Cooling.write(Signal.High);

                // initialization
                double temperature=0.0;
                int mfgId=0;
                int deviceId=0;
                int config=0;

                // sensor configuration
                int i=1;
                config = sensor.readConfiguration(1);
                System.out.println("config=:" + config);

                mfgId = sensor.readManufacturingId(6);
                System.out.println("MfgId=:" + mfgId);
                deviceId = sensor.readDeviceId(7);
                System.out.println("DeviceId=:" + deviceId);

                while( true ) {

                    currentTime += 1;

                    atr.requestSyncStart();

                    while(  ( interactionRoot = getNextInteractionNoWait() ) != null ) {
                        ControlMessage msg = (ControlMessage)interactionRoot;
                        System.out.println( "HVAC: Received ControlMessage interaction" );

                        boolean fan1On = msg.get_fan1();
                        boolean fan2On = msg.get_fan2();
                        boolean heaterOn = msg.get_heater();
                        boolean coolerOn = msg.get_cooler();

                        temperature = sensor.readTemperature(5);
                        System.out.println("Temperature=:" + temperature);

                        SensorMessage sensor_msg = create_SensorMessage();
                        sensor_msg.set_value( temperature );
                        sensor_msg.set_units( "deg C" );
                        sensor_msg.sendInteraction( getRTI(), currentTime );

                        if (heaterOn)
                            relay1Heating.write(Signal.Low);
                        else
                            relay1Heating.write(Signal.High);
                        if (coolerOn)
                            relay2Cooling.write(Signal.Low);
                        else
                            relay2Cooling.write(Signal.High);
                        if (fan1On)
                            relay3Fan1.write(Signal.Low);
                        else
                            relay3Fan1.write(Signal.High);
                        if (fan2On)
                            relay4Fan2.write(Signal.Low);
                        else
                            relay4Fan2.write(Signal.High);
                    }

                    AdvanceTimeRequest newATR = new AdvanceTimeRequest( currentTime );
                    putAdvanceTimeRequest( newATR );

                    atr.requestSyncEnd();
                    atr = newATR;
                }

            }
            public static void main( String[] args ) {
                try {
                    HVAC hvac = new HVAC( args );
                    hvac.execute();
                } catch ( Exception e ) {
                    System.err.println( "Exception caught: " + e.getMessage() );
                    e.printStackTrace();
                }
            }
        }
    ```

    * Copy `TemperatureSensor.java` into this folder

      * add `package BBBHelloWorld;` to the top of this file.

  * Edit the file `$C2WTROOT/build.properties`

    * Add the following properties under the `helloworld` properties:

    ```bash
    bbbhelloworld.dir = ${examples.dir}/BBBHelloWorld
    src.bbbhelloworld.java.dir = ${bbbhelloworld.dir}/java
    build.java.bbbhelloworld.dir = ${build.java.core.dir}
    src.generated.bbbhelloworld.dir = ${src.generated.dir}/BBBHelloWorld
    src.generated.bbbhelloworld.java.dir = ${src.generated.bbbhelloworld.dir}/java
    ```

  * Edit the file `$C2WTROOT/build.xml`

    * add the following target under the `compile-HelloWorld` target:

    ```xml
    <target name="compile-BBBHelloWorld" depends="compile-core">
            <javac source="1.6" target="1.6" destdir="${build.java.bbbhelloworld.dir}" debug="on" debuglevel="lines,vars,source" includejavaruntime="yes" includeantruntime="yes" failonerror="false" nowarn="true">
                    <!--<compilerarg value="-verbose"/>
                    <compilerarg value="-Xlint:all"/>-->

                    <src path="${src.generated.bbbhelloworld.java.dir}"/>
                    <src path="${src.bbbhelloworld.java.dir}"/>
                    <classpath refid="c2wt.class.path"/>
            </javac>
            <copy todir="${build.java.bbbhelloworld.dir}">
                    <fileset dir="${src.bbbhelloworld.java.dir}" includes="**/*.xml, **/*.properties, **/*.txt, **/*.jpg, **/*.bmp, **/*.ico, **/*.stg"/>
            </copy>
    </target>
    ```

    * Add `compile-BBBHelloWorld` to the `depends` list of the the target `compile`

## Code Compilation

Compile the code by running `ant` from `$C2WTROOT` or by opening the
Omnet++ IDE and running `c2wt build.xml` option from the run custom
command toolbar button menu.

## Configure the BBB

Because the libbulldog code uses the GPIO, it requires **root**
permissions when running the program.  For this reason it is
recommended that everything for this sample be placed in the root
directory `/root`, and use **root** as the username in the model for
the BBB computer model.

Configure ssh keys; It is recommended to use the same ssh key that s
used for the C2WT machine as for the BBB.  In this case just copy the
public key to the BBB and append it to `/root/.ssh/authorized_keys`:

* On the C2WT machine:

	```bash
    ssh-keygen -y -f id_rsa > id_rsa.pub
    scp id_rsa.pub root@<BBB IP>:/root/.ssh/.
    ```

* On the BBB:  

    ```bash
    cat id_rsa.pub >> /root/.ssh/authorized_keys
    ```

* install libraries:

  ```bash
  sudo apt-get install curl libjava3d-java openjdk-7-jdk xvfb ant
  ```

* Download portico from [portico sourceforge](http://sourceforge.net/projects/portico/files/Portico/portico-1.0.2/):

  ```bash
  wget http://downloads.sourceforge.net/project/portico/Portico/portico-1.0.2/portico-1.0.2-linux.tar.gz
  tar xvf portico-1.0.2-linux.tar.gz
  ```

* Environment Variable Configuration: add the following to the end of the `$HOME/.bashrc` and `/etc/profile` files on your BBB:

  ```bash
  export C2WTROOT=$HOME/Projects/c2wt
  export RTI_HOME=<PATH TO PORTICO DIRECTORY>, e.g. export RTI_HOME=$HOME/portico-1.0.2
  export JAVA_HOME=<PATH TO JAVA_HOME>, e.g. export JAVA_HOME=/usr/lib/jvm/java-7-openjdk-armhf
  ```

  where `$HOME` is `/root` in this case, because the HVAC controller requires **root** permissions to run.

## Copyying Files

* **On the BBB**, make the `Projects` folder:

  ```bash
  mkdir -p $HOME/Projects
  ```

* **From the C2WT machine**, copy the c2wt folder to the BBB:

  ```bash
  scp -r $C2WTROOT <BBB user-name>@<BBB IP>:$HOME/Projects/.
  ```

* Recompile the processID library for ARM architecture **on the BBB**:

  ```bash
  chmod +x $C2WTROOT/core/src/cpp/ProcessId/buildProcessIdJNI.sh
  $C2WTROOT/core/src/cpp/ProcessId/buildProcessIdJNI.sh
  ```

* Make the remote start script **on the BBB** executable:
  
  ```bash
  chmod +x $C2WTROOT/generated/BBBHelloWorld/scripts/main-Deployment/<BBB IP>/Remote/start.sh
  ```

* Make the start script **on the C2WT machine** executable:
  
  ```bash
  chmod +x $C2WTROOT/generated/BBBHelloWorld/scripts/main-Deployment/Main/start.sh
  ```

## Run the Federates

* On the C2WT Machine:

	```bash
	$C2WTROOT/generated/BBBHelloWorld/scripts/main-Deployment/Main/start.sh
	```

* On the BBB:

	```bash
	$C2WTROOT/generated/BBBHelloWorld/scripts/main-Deployment/<BBB IP>/Remote/start.sh
	```
