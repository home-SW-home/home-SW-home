from gpiozero import Button
from time import sleep
import time
import sys

class inputs_listener:
    """listen and logs all input events"""

    button=None
    csv_file="/var/log/raw_inputs.csv"
    f=None
    last_input=(0,0,"")

    def __init__(self):
        try:
            self.f=open(self.csv_file,"a")
        except Exception as e:
            print("%s" % str(e))
            exit(-1)

    def pressed(self, button):
        self.button=button
        self.log_csv('pressed')

    def released(self, button):
        self.button=button
        self.log_csv('released')

    def log_csv(self, action):
        now=self.get_now()
        new_input=(now, self.button.pin, action)
        # drop repeating identical events (within 100ms)
        if ((new_input[1] == self.last_input[1]) and    # same gpio
            (new_input[0] - self.last_input[0] < 100)): # almost same time
            pass # noise
        else:
            csv=("%s,%s,%s\n" % new_input)
            self.last_input=new_input
            #print(csv)
            try:
                self.f.write(csv)
                self.f.flush()
            except Exception as e:
                print("%s" % str(e))
                exit(-1)

    def get_now(self):
        return int(time.time()*1000)


print "inputs listen started."

button={}
buttons_gpios=[16,17,18,19,20,21,22,23,24,25,26,27]
for n in buttons_gpios:
    button[n]=Button(n, pull_up=False)


il=inputs_listener()
while True:
    # listen on all buttons
    for n in buttons_gpios:
        button[n].when_pressed = il.pressed
        button[n].when_released = il.released
    sleep(0.01)


