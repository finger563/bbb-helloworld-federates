package BBBHelloWorld;


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

        boolean heaterOn = true;
        boolean coolerOn = false;
        boolean fan1On = true;
        boolean fan2On = true;

        while( true ) {
            ControlMessage msg = create_ControlMessage();
            msg.set_heater( heaterOn );
            msg.set_cooler( coolerOn );
            msg.set_fan1( fan1On );
            msg.set_fan2( fan2On );

            currentTime += 1;
            atr.requestSyncStart();

            System.out.println( "Controller: Sending ControlMessage interaction" );
            msg.sendInteraction( getRTI(), currentTime );

            AdvanceTimeRequest newATR = new AdvanceTimeRequest( currentTime );
            putAdvanceTimeRequest( newATR );

            atr.requestSyncEnd();
            atr = newATR;

            // Switch fan settings
            heaterOn = !heaterOn;
            coolerOn = !heaterOn;
            fan1On = !fan1On;
            fan2On = !fan2On;
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
