/**
 *  Genius Hub Room
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
  definition (name: 'Genius Hub Room', namespace: 'cwm', author: 'Neil Cumpstey') {
		capability 'Actuator'
		capability 'Illuminance Measurement'
		capability 'Temperature Measurement'
		capability 'Thermostat'
		capability 'Refresh'
		capability 'Sensor'
		capability 'Battery'
		capability 'Health Check'

    command 'refresh'
    command 'setTemperature'

    // attribute 'minBattery', 'number'
    attribute 'operatingMode', 'string'
    // attribute 'sensorTemperature', 'number'
    // attribute 'setTemperature', 'number'
    // attribute 'luminance', 'number'
  }

  preferences {
  }

  tiles(scale: 2) {
    multiAttributeTile(name:"thermostat", type:"generic", width:6, height:4) {
      tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
        attributeState("temperature", label:'${currentValue}°C', icon: "st.alarm.temperature.normal", defaultState: true,
					backgroundColors:[
            [value: 0, color: "#153591"],
            [value: 7, color: "#1e9cbb"],
            [value: 15, color: "#90d2a7"],
            [value: 23, color: "#44b621"],
            [value: 28, color: "#f1d801"],
            [value: 35, color: "#d04e00"],
            [value: 37, color: "#bc2323"],
            
            [value: 60, color: "#153591"],
            [value: 67, color: "#1e9cbb"],
            [value: 72, color: "#90d2a7"],
            [value: 77, color: "#44b621"],
            [value: 83, color: "#f1d801"],
            [value: 88, color: "#d04e00"],
            [value: 93, color: "#bc2323"]
					]
        )
      }
      // Range doesn't seem to work :-(
			tileAttribute ('device.targetTemperature', key: 'SLIDER_CONTROL', range: '(4..28)') {
				attributeState 'level', action: 'setTemperature'
			}
    }
    standardTile('brand', 'device', width: 1, height: 1, decoration: 'flat') {
      state 'default', label: '', icon: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-60.png'
    }
    // TODO: better icons here
    valueTile('operatingMode', "device.operatingMode", width: 1, height: 1, decoration: 'flat') {
      state('off', label: '', icon: "st.Weather.weather7", defaultState: true)
      state('override', icon: "st.Home.home1")
      state('timer', icon: "st.Health & Wellness.health7")
      state('footprint', icon: "st.People.people6")
    }
    standardTile('refresh', 'device', width: 1, height: 1, decoration: 'flat') {
      state 'default', label: '', action: 'refresh', icon: 'st.secondary.refresh'
    }
    valueTile('battery', 'device.battery', inactiveLabel: false, width: 1, height: 1) {
      state 'battery', label: '${currentValue}% battery', unit: '%',
      backgroundColors:[
        [value: 10, color: '#bc2323'],
        [value: 26, color: '#f1d801'],
        [value: 51, color: '#44b621']
      ]
    }
		valueTile('illuminance', 'device.illuminance', inactiveLabel: false, width: 1, height: 1) {
			state 'illuminance', label: '${currentValue} lux', unit: 'lux'
    }

    main(['thermostat'])
    details(['thermostat', 'brand', 'operatingMode', 'refresh', 'battery', 'illuminance'])
  }
}

//#region Methods called by parent app

void setGeniusId(Integer geniusId) {
  state.geniusId = geniusId
}

void setLogLevel(Integer logLevel) {
  state.logLevel = logLevel
}

Integer getGeniusId() {
  return state.geniusId
}

String getGeniusType() {
  return 'room'
}

void updateState(Map values) {
  logger "${device.label}: updateState: ${values}", 'trace'

  if (values?.containsKey('operatingMode')) {
    sendEvent(name: 'operatingMode', value: values.operatingMode)
  }

  if (values?.containsKey('sensorTemperature')) {
    sendEvent(name: 'temperature', value: values.sensorTemperature, unit: '°C')
  }

  if (values?.containsKey('minBattery')) {
    sendEvent(name: 'battery', value: values.minBattery, unit: '%')
  }

  if (values?.containsKey('illuminance')) {
    sendEvent(name: 'illuminance', value: values.illuminance, unit: 'lux')
  }
}

//#endregion Methods called by parent app

//#region Actions

def setTemperature(value) {
  logger "${device.label}: setTemperature: ${value}", 'trace'

  sendEvent(name: 'targetTemperature', value: value, unit: '°C')  

  parent.pushRoomTemperature(state.geniusId, value)
}

//#endregion Actions

def parse(String description) {
}

def refresh() {
  logger "${device.label}: refresh", 'trace'
  
  parent.refresh()
}

void logger(msg, level = 'debug') {
  switch (level) {
    case 'error':
      if (state.logLevel >= 1) log.error msg
      break
    case 'warn':
      if (state.logLevel >= 2) log.warn msg
      break
    case 'info':
      if (state.logLevel >= 3) log.info msg
      break
    case 'debug':
      if (state.logLevel >= 4) log.debug msg
      break
    case 'trace':
      if (state.logLevel >= 5) log.trace msg
      break
    default:
      log.debug msg
      break
  }
}
