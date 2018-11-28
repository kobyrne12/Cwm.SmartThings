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
    multiAttributeTile(name: 'thermostat', type: 'generic', width: 6, height: 4) {
      tileAttribute('device.temperature', key: 'PRIMARY_CONTROL') {
        attributeState('temperature', label: '${currentValue}°', icon: 'st.alarm.temperature.normal', defaultState: true,
          backgroundColors:[
            [ value: 30, color: '#3366ff' ],
            [ value: 50, color: '#00ccff' ],
            [ value: 60, color: '#00ffcc' ],
            [ value: 65, color: '#00cc00' ],
            [ value: 70, color: '#66ff33' ],
            [ value: 75, color: '#f1d801' ],
            [ value: 80, color: '#d04e00' ],
            [ value: 85, color: '#bc2323' ]
          ]
        )
      }
      // Range doesn't seem to work :-(
      // But we wouldn't be able to control this depending on the temperature scale anyway, and this way anyone who likes Fahrenheit can still use it.
      tileAttribute ('device.targetTemperature', key: 'SLIDER_CONTROL', range: '(4..28)') {
        attributeState 'level', label: '${currentValue}°', action: 'setTemperature'
      }
    }
    standardTile('brand', 'device', width: 1, height: 1, decoration: 'flat') {
      state 'default', label: '', icon: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-60.png'
    }
    // TODO: better icons here
    valueTile('operatingMode', "device.operatingMode", width: 1, height: 1, decoration: 'flat') {
      state('off', label: '', icon: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-off-120.png', defaultState: true)
      state('override', icon: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-override-120.png')
      state('timer', icon: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-timer-120.png')
      state('footprint', icon: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-footprint-120.png')
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
    def value = convertCelsius(values.sensorTemperature)
    sendEvent(name: 'temperature', value: value, unit: "°${temperatureScale}")
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

  sendEvent(name: 'targetTemperature', value: value, unit: "°${temperatureScale}")  

  def valueInCelsius = temperatureScale == 'F' ? convertFahrenheit(value) : convertCelsius(value)
  parent.pushRoomTemperature(state.geniusId, valueInCelsius)
}

def parse(String description) {
}

def refresh() {
  logger "${device.label}: refresh", 'trace'
  
  parent.refresh()
}

//#endregion Actions

//#region Helpers

private convertCelsius(Float valueInCelsius) {
  def value = (temperatureScale == "F") ? ((valueInCelsius * 1.8) + 32) : valueInCelsius
  return value.round(1)
}

private convertFahrenheit(Float valueInFahrenheit) {
  def value = (temperatureScale == "C") ? ((valueInFahrenheit - 32) / 1.8) : valueInFahrenheit
  return value.round(1)
}

private logger(msg, level = 'debug') {
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

//#endregion Helpers
