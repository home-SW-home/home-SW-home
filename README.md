# home-SW-home - smart**er** home automation

## What is home-SW-home
Pronounced "home-switch-home", it is a smart control for your home
automation system. It started as a playground for Clojure learning. Let's see
where it takes us.
The Clojure code runs on a Raspberry PI 3, and it is based on event machine
responding to inputs and generating outputs.
It interfaces with relay setup hardware to drive the house.


## Getting started
* install raspbian
* run the install script


    sudo sh install_home-SW-home_root.sh


More details on [INSTALL](INSTALL) file.


## Current state
* Each input (switch) has its corresponding output (light or electricity socket).
* Pressing 2 buttons together results in advanced mode with a different outputs set.
* Pressing 3 buttons together results in a special programmable outputs set.


## Possible next directions
* https://github.com/blandinw/cherry
* http://cmusphinx.sourceforge.net
* External crypto-signed commands
* https://home-assistant.io


## Some low level hints
Raw API is a CSV file for inputs:

    1476177100258,GPIO19,pressed
    1476177100420,GPIO19,released
    1476177101428,GPIO18,pressed
    1476177101592,GPIO18,released

and for outputs:

    1476177105226,2,on
    1476177105227,5,on
    1476177105814,7,off
    1476177105815,4,off


## What does the installer do
adds to /etc/rc.local

    su -c /home/pi/home-SW-home/tmux-start pi

The following windows run inside tmux:

    python inputs_listen.py               # generates /var/log/raw_inputs.csv
    python outputs_apply.py               # reads /var/log/raw_outputs.csv and applies
    tail -n 0 -f /var/log/raw_inputs.csv | ./event_machine.clj
                                          # generates /var/log/raw_output.csv
    tail -n 0 -f /var/log/raw_inputs.csv  # shows inputs
    tail -n 0 -f /var/log/raw_outputs.csv # shows outputs

to enter tmux:

    tmux attach

to exit tmux:

    CTRL-a CTRL-d

to switch to next window:

    CTRL-a CTRL-n

## License
AGPLv3 Licence

## Language
Clojure

