# mh-z19b-driver
mh-z19b-driver is a java library that operates CO2 gas concentration sensor called [MH-Z19B](https://www.winsen-sensor.com/d/files/infrared-gas-sensor/mh-z19b-co2-ver1_0.pdf) to connect MH-Z19B to GPIO terminal of Raspberry Pi 3B and make it for use in java. I releases this in the form of the Eclipse plug-in project.
You need Java 8 or higher.

I use [jSerialComm](https://github.com/Fazecast/jSerialComm)
for serial communication in java and have confirmed that it works in Raspberry Pi 3B ([Raspbian Buster Lite OS](https://www.raspberrypi.org/downloads/raspbian/) (2019-07-10)).

## Connection of MH-Z19B and Raspberry Pi 3B
**Connect with `Vin <--> Vin`,`GND <--> GND`, but connect Tx and Rx mutually to enable transmission / reception. `Tx <--> Rx`,` Rx <--> Tx`**
- `6. Pins` of [MH-Z19B](https://www.winsen-sensor.com/d/files/infrared-gas-sensor/mh-z19b-co2-ver1_0.pdf)
  - Vin
  - GND
  - Tx
  - Rx
- [GPIO of Raspberry Pi 3B](https://www.raspberrypi.org/documentation/usage/gpio/README.md)
  - Vin --> (2) or (4)
  - GND --> (6), (9), (14), (20), (25), (30), (34) or (39)
  - Tx --> (8) GPIO14
  - Rx --> (10) GPIO15
  
In addition, I was able to use MH-Z19B using **DSD TECH SH-U09C USB to TTL Serial Adapter with FTDI FT232RL Chip** instead of GPIO of Raspberry Pi 3B. At that time, I set `/dev/ttyUSB0` as the serial port name.
  
## Install Raspbian Buster Lite OS (2019-07-10)
The reason for using this version is that it is the latest as of July 2019 and [BlueZ](http://www.bluez.org/) 5.50 is included from the beginning, and use Bluetooth and serial communication simultaneously.

## Configuration of Raspbian Buster Lite OS
- Edit `/boot/cmdline.txt`
```
console=serial0,115200 --> removed
```
- Edit `/boot/config.txt`
```
@@ -45,7 +45,7 @@
 # Uncomment some or all of these to enable the optional hardware interfaces
 #dtparam=i2c_arm=on
 #dtparam=i2s=on
-#dtparam=spi=on
+dtparam=spi=on
 
 # Uncomment this to enable the lirc-rpi module
 #dtoverlay=lirc-rpi
@@ -55,6 +55,10 @@
 # Enable audio (loads snd_bcm2835)
 dtparam=audio=on
 
+enable_uart=1
+dtoverlay=pi3-miniuart-bt
+core_freq=250
+
 [pi4]
 # Enable DRM VC4 V3D driver on top of the dispmanx display stack
 dtoverlay=vc4-fkms-v3d
```
When editing is complete, reboot. Now you can use `/dev/ttyAMA0` for serial communication without conflicting with Bluetooth.

## Install jdk8 on Raspberry Pi 3B
For example, the installation of OpenJDK 8 is shown below.
```
# apt-get update
# apt-get install openjdk-8-jdk
```

## Install git
If git is not included, please install it.
```
# apt-get install git
```

## Use this with the following bundles
- [SLF4J 1.7.26](https://www.slf4j.org/)
- [jSerialComm 2.5.1](https://mvnrepository.com/artifact/com.fazecast/jSerialComm/2.5.1)

I would like to thank the authors of these very useful codes, and all the contributors.

## How to use
The following sample code will be helpful. Reading sensor data immediately after open may give a strange value, but I think that the normal values will be obtained after the second time.
```java
import io.github.s5uishida.iot.device.mhz19b.driver.MHZ19BDriver;

public class MyMHZ19B {
    private static final Logger LOG = LoggerFactory.getLogger(MyMHZ19B.class);
    
    public static void main(String[] args) throws IOException {
        MHZ19BDriver mhz19b = null;
        try {
            mhz19b = MHZ19BDriver.getInstance("/dev/ttyAMA0");
            mhz19b.open();
            mhz19b.setDetectionRange5000();
            
            while (true) {
                int value = mhz19b.getGasConcentration();
                LOG.info("co2:" + value);
                
                Thread.sleep(10000);
            }
        } catch (InterruptedException e) {
            LOG.warn("caught - {}", e.toString());
        } catch (IOException e) {
            LOG.warn("caught - {}", e.toString());
        } finally {
            if (mhz19b != null) {
                mhz19b.close();
            }
        }
    }
}
```
