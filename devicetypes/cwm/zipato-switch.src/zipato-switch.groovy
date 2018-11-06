/**
 * Zipato switch device handler
 * Version: 0.1
 * Date: 5 Nov 2018
 * Author: Neil Cumpstey
 * Copyright: Neil Cumpstey
 * 
 * A SmartThings device handler which wraps a virtual device on a Zipato box.
 * It can be used to interact with devices such as LightwaveRF, which are not compatible with a SmartThings hub.
 */
metadata {
	definition (name: "Zipato switch", namespace: "cwm", author: "Neil Cumpstey") {
    capability "Actuator"
		capability "Switch"
	}

  preferences {
    input("remotingUrl", "text", title: "Remoting url", description: "Url provided by Zipato to control the virtual device. The value will be tacked on the end.")
  }

  tiles(scale: 2) {
    multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
      tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
        attributeState "on", label:'${name}', action:"switch.off", icon:"st.Home.home30", backgroundColor:"#00A0DC", nextState:"turningOff"
        attributeState "off", label:'${name}', action:"switch.on", icon:"st.Home.home30", backgroundColor:"#FFFFFF", nextState:"turningOn", defaultState: true
        attributeState "turningOn", label:'Turning On', action:"switch.off", icon:"st.Home.home30", backgroundColor:"#00A0DC", nextState:"turningOn"
        attributeState "turningOff", label:'Turning Off', action:"switch.on", icon:"st.Home.home30", backgroundColor:"#FFFFFF", nextState:"turningOff"
      }
    }

    standardTile("explicitOn", "device.switch", width: 2, height: 2, decoration: "flat") {
      state "default", label: "On", action: "switch.on", icon: "st.Home.home30", backgroundColor: "#ffffff"
    }
    standardTile("explicitOff", "device.switch", width: 2, height: 2, decoration: "flat") {
      state "default", label: "Off", action: "switch.off", icon: "st.Home.home30", backgroundColor: "#ffffff"
    }

    main(["switch"])
    details(["switch", "explicitOn", "explicitOff"])
  }
}

def parse(String description) {
}

def on() {
	log.debug "Executing 'on'"
  sendEvent(name: "switch", value: 'turningOn', isStateChange: true)
  request(1, 'on')
}

def off() {
  log.debug "Executing 'off'"
  sendEvent(name: "switch", value: 'turningOff', isStateChange: true)
  request(0, 'off')
}

private def request(value, nextState) {
  def params = [
    uri: settings.remotingUrl + value,
  ]

  httpGet(params) {response ->
    log.debug "Response data from '" + nextState + "' command: " + response.data
    sendEvent(name: "switch", value: nextState, isStateChange: true)
  }    
}