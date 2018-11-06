# Zipato switch

https://github.com/cumpstey/SmartThings/tree/master/devices/zipato-switch

## Overview

A SmartThings device handler which interacts with a device set up on a Zipabox. It can be used to interact with, for example, LightwaveRF devices installed on a Zipabox, which are incompatible with SmartThings.

It's not possible to interact directly with a real device on a Zipabox using a remoting url. Instead, a virtual device can be created, and a rule used to map the interactions with the virtual device to the real device.

## Installation

1. Create a virtual device in the Zipabox. I have a virtual meter called Interactions set up for such things, which provides multiple attributes for different purposes nicely contained in a single virtual device.
    
    <img src="https://raw.githubusercontent.com/cumpstey/SmartThings/master/documentation/devicetypes/zipato-switch/assets/zipabox-virtual-meter.jpg" width="200">

2. Create a rule mapping 1 and 0 inputs to the virtual device to appropriate behaviour of the real device.

    <img src="https://raw.githubusercontent.com/cumpstey/SmartThings/master/documentation/devicetypes/zipato-switch/assets/zipabox-example-rule.jpg" width="200">

3. Install the device handler in the SmartThings IDE, and create a new device using the handler.

4. Edit the device settings, and add in the Zipato remoting url for the appropriate attribute of your virtual device.
