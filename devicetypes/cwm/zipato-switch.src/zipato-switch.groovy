/**
 *  Zipato Switch
 * 
 *  Copyright 2018 Neil Cumpstey
 * 
 *  A SmartThings device handler which wraps a virtual device on a Zipato box.
 *  It can be used to interact with devices such as LightwaveRF, which are not compatible with a SmartThings hub.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "Zipato Switch", namespace: "cwm", author: "Neil Cumpstey", vid: "generic-switch") {
    capability "Actuator"
		capability "Switch"
    capability "Health Check"
	}

  preferences {
    input("remotingUrl", "text", title: "Remoting url", description: "Url provided by Zipato to control the virtual device. The value will be tacked on the end.")
    input("logging", "bool", title: "Debug logging", description: "Enable logging of debugging messages.")
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
  log "Parsing: " + description
}

def on() {
	log "Executing 'on'"
  sendEvent(name: "switch", value: 'turningOn', isStateChange: true)
  request(1, 'on')
}

def off() {
  log "Executing 'off'"
  sendEvent(name: "switch", value: 'turningOff', isStateChange: true)
  request(0, 'off')
}

private def request(value, nextState) {
  def params = [
    uri: settings.remotingUrl + value,
  ]

  httpGet(params) { response ->
    log "Response data from '" + nextState + "' command: " + response.data
    sendEvent(name: "switch", value: nextState, isStateChange: true)
  }    
}

private def log(message) {
  if (settings.logging){
    log.debug "$message"
  }
}