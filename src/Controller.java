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
		value = msg.get_Value();
		units = msg.get_Units();
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
