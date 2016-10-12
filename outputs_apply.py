from gpiozero import LED
from time import sleep
import time

leds_gpios=[2,3,4,5,6,7,8,9,10,11,12,13,14,15]
led={}
max_delay=2000

for n in leds_gpios:
    led[n]=LED(n)

def get_now():
    return int(time.time()*1000)

def follow(thefile):
    thefile.seek(0,2)      # Go to the end of the file
    while True:
         line = thefile.readline()
         if not line:
             time.sleep(0.01)    # Sleep briefly
             continue
         yield line


# start with all leds off
for lg in leds_gpios:
    led[lg].off()

print "outputs apply started."

logfile = open("/var/log/raw_outputs.csv")
loglines = follow(logfile)
for line in loglines:
    # validate inputs try catch
    try:
        (epoch_str,gpio_str,action_str)=line.strip().split(',')
        # sanity checks for epoch (near now)
        epoch=int(epoch_str)
        delay = get_now()-epoch
        if (delay > max_delay):
            print "delay:",delay 
        # sanity checks for gpio and action
        gpio=int(gpio_str)
        leds_gpios.index(gpio) # verify that gpio is within range or ValueError
        if (action_str == "on"):
            led[gpio].on()
        else:
            if (action_str == "off"):
                led[gpio].off()
            else:
                if (action_str == "toggle"):
                    led[gpio].toggle()
                else:
                    print "invalid action"
    except ValueError, KeyError:
        print "invalid gpio"

