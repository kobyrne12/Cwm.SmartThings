/**
 *  Genius Hub House
 * 
 *  Copyright 2018 Neil Cumpstey
 * 
 *  A SmartThings device handler which wraps a device on a Genius Hub.
 *
 *  ---
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
metadata {
  definition (name: 'Genius Hub House', namespace: 'cwm', author: 'Neil Cumpstey') {
		capability "Actuator"
		capability "Temperature Measurement"
		capability "Thermostat"
		capability "Refresh"
		capability "Sensor"
		capability "Health Check"

    command 'refresh'
  }

  preferences {
  }

  tiles(scale: 2) {
		multiAttributeTile(name:"temperature", type:"generic", width:3, height:2, canChangeIcon: true) {
			tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
				attributeState("temperature", label:'${currentValue}°', icon: "st.alarm.temperature.normal",
					backgroundColors:[
							[value: 0, color: "#153591"],
							[value: 7, color: "#1e9cbb"],
							[value: 15, color: "#90d2a7"],
							[value: 23, color: "#44b621"],
							[value: 28, color: "#f1d801"],
							[value: 35, color: "#d04e00"],
							[value: 37, color: "#bc2323"],
					]
				)
			}
		}
    standardTile('brand', 'device', width: 1, height: 1, decoration: 'flat') {
      state 'default', label: '', icon: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-60.png'
    }
    standardTile('refresh', 'device', width: 1, height: 1, decoration: 'flat') {
      state 'default', label: '', action: 'refresh', icon: 'st.secondary.refresh'
    }

    main(['switch'])
    details(['switch', 'brand', 'refresh','overrideTime',  'explicitOn', 'explicitOff'])
  }
}

//#region Methods called by parent app

void setGeniusId(geniusId) {
  parent.logger "${device.label}: setGeniusId", 'trace'

  state.geniusId = geniusId
}

String getGeniusId() {
  parent.logger "${device.label}: getGeniusId", 'trace'

  return "${state.geniusId}"
}

String getGeniusType() {
  parent.logger "${device.label}: getGeniusType", 'trace'

  return 'house'
}

//#endregion Methods called by parent app

def parse(String description) {
}

def refresh() {
  parent.logger "${device.label}: refresh", 'trace'
//sendEvent(name:'time', value:8)
  parent.refresh()
}
