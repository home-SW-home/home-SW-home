#!/bin/bash

if ! [ $(id -u) = 1000 ]; then
  echo "Must be run by user pi!"
  exit 1
fi

# install lein
mkdir -p ~/bin
cd ~/bin
wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein
chmod a+x lein

# update PATH
echo "export PATH=\$PATH:/opt/jdk1.8.0_111/bin:~/bin/" >> /home/pi/.bashrc

# update profiles.clj
mkdir -p ~/.lein
echo "{:user {:plugins [[cider/cider-nrepl \"0.8.1\"] [lein-exec \"0.3.6\"] [pallet-fsm \"0.2.0\"] ]}}" > ~/.lein/profiles.clj

# tmux configuration
cp /home/pi/home-SW-home/tmux.conf ~/.tmux.conf

# run lein for the first time to download required libraries
export PATH=$PATH:/opt/jdk1.8.0_111/bin:/home/pi/bin/
lein -v


