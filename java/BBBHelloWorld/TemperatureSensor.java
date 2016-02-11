
package BBBHelloWorld;

import org.bulldog.core.io.bus.i2c.I2cDevice;
import org.bulldog.core.io.bus.i2c.I2cConnection;
import org.bulldog.core.io.bus.i2c.I2cBus;


public class TemperatureSensor extends I2cDevice {

	byte REG_ADDR_RESULT=0x00;
	byte REG_ADDR_CONFIG=0x02;



	public TemperatureSensor(I2cBus bus, int address) {
		super(bus, address);
	}

	public TemperatureSensor(I2cConnection connection) 	{
		super(connection);
	}

	//throws java.io.IOException
	void 	initMode(int mode)  throws java.io.IOException{

	}



	double 	readTemperature(int register) throws java.io.IOException{
		byte[] readingValue={0,0};
		int rt=0;
		double temp=0;


		rt=readBytesFromRegister(register, readingValue);

		System.out.println("Read Temperature!!!");


		if((readingValue[0] & 0x80) == 0x80){
			//System.out.println("Ta is Tcrit");
		}
		if((readingValue[0] & 0x40) == 0x40){
			//System.out.println("Ta > Tupper");
		}			
		if((readingValue[0] & 0x20) == 0x20){
			//System.out.println("Ta < Tlower");
		}


		readingValue[0] = (byte) (readingValue[0] & 0x1F); // clear flag bits
		//System.out.println("Upper byte after=:"+readingValue[0]);

		// convert lower byte into unsgined int
		double lowerByte=  readingValue[1] & 0xff;
		//System.out.println("lower byte=:"+Integer.toHexString(lowerByte));
		//System.out.println("lower byte=:"+lowerByte);

		if((readingValue[0] & 0x10) == 0x10) //t<0
		{


			readingValue[0] = (byte) (readingValue[0] & (byte)0x0F);
			//System.out.println("Upper byte=:"+readingValue[0]);

			temp= 256.0 - (readingValue[0]*16 + lowerByte/16);
		}else
		{

			//System.out.println("lower byte after=:"+lowerByte);			
			temp= (readingValue[0]*16 + lowerByte/16);
		}		


		return temp;
	}

	int readConfiguration(int register) throws java.io.IOException {

		byte[] readingValue={0,0};
		int rt=0;		
		int config=0;
		rt=readBytesFromRegister(register, readingValue); 


		// bit 15-0 is for device manufacturing id
		config= (readingValue[0] << 8) + readingValue[1];	

		return config;
	}		


	int readManufacturingId(int register) throws java.io.IOException {

		byte[] readingValue={0,0};
		int rt=0;		
		int mfgId=0;
		rt=readBytesFromRegister(register, readingValue); 
		System.out.println("Read MfgId!!!");	

		// bit 15-0 is for device manufacturing id
		mfgId= (readingValue[0] << 8) + readingValue[1];	
		//System.out.println("MfgId=:"+mfgId);		
		return mfgId;
	}	

	int readDeviceId(int register) throws java.io.IOException {

		byte[] readingValue={0,0};
		int rt=0;
		int deviceId=0;
		rt=readBytesFromRegister(register, readingValue);
		System.out.println("Read DeviceId!!!");	

		// Bit 15-8 is for device ID, bit 7-0 is for device revision
		deviceId= readingValue[0];// bit 15-8	
		//System.out.println("DeviceId=:"+deviceId);
		return deviceId;
	}

}
