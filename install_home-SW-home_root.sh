#!/bin/bash

if ! [ $(id -u) = 0 ]; then
  echo "Must be run by user root!"
  exit 1
fi

# update system
apt-get update
apt-get upgrade -y

# install required packages
apt-get install -y python-gpiozero tmux
apt-get install --reinstall -y python-pkg-resources

# install additionl nice to have packages
apt-get install -y vim emacs24

# install oracle java
wget --no-check-certificate --no-cookies --header "Cookie: oraclelicense=accept-securebackup-cookie" http://download.oracle.com/otn-pub/java/jdk/8u111-b14/jdk-8u111-linux-arm32-vfp-hflt.tar.gz
tar zxvf jdk-8u111-linux-arm32-vfp-hflt.tar.gz -C /opt

# update boot params
echo "dwc_otg.lpm_enable=0 console=serial0,115200 console=tty1 root=/dev/mmcblk0p2 rootfstype=ext4 elevator=deadline fsck.repair=yes rootwait bcm2708.vc_i2c_override=1" > /boot/cmdline.txt

# kernel modules
echo "i2c-dev" >> /etc/modules
#echo "/dev/i2c-1" >> /etc/modules

# rc.local
cp -f /home/pi/home-SW-home/rc.local /etc

# csv files
touch /var/log/raw_inputs.csv
touch /var/log/raw_outputs.csv
chown pi:pi /var/log/raw_inputs.csv
chown pi:pi /var/log/raw_outputs.csv

# lein link
ln -s /home/pi/bin/lein /usr/local/bin/lein

# and now as user pi
chmod a+x /home/pi/home-SW-home/install_home-SW-home_pi.sh
su -c /home/pi/home-SW-home/install_home-SW-home_pi.sh pi

echo "Installation of home-SW-home is complete."
echo "Please reboot ..."
