/**
 *  Genius Hub Room
 * 
 *  Copyright 2018 Neil Cumpstey
 * 
 *  A SmartThings device handler which wraps a device on a Genius Hub.
 *
 *  ---
 *  Disclaimer:
 *  This device handler and the associated smart app are in no way sanctioned or supported by Genius Hub.
 *  All work is based on an unpublished api, which may change at any point, causing this device handler or the
 *  smart app to break. I am in no way responsible for breakage due to such changes.
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
    capability 'Refresh'
    capability 'Sensor'
    capability 'Battery'
    capability 'Health Check'

    command 'extraHour'
    command 'refresh'
    command 'revert'
    command 'setTargetTemperature'

    attribute 'operatingMode', 'string'
    attribute 'overrideEndTime', 'date'
    attribute 'overrideEndTimeDisplay', 'string'
  }

  preferences {
  }

  tiles(scale: 2) {
    multiAttributeTile(name: 'thermostat', type: 'generic', width: 6, height: 4) {
      tileAttribute('device.temperature', key: 'PRIMARY_CONTROL') {
        attributeState('temperature', label: '${currentValue}°', icon: 'st.alarm.temperature.normal', defaultState: true,
          backgroundColors: [
            [value: 30, color: '#153591'],
            [value: 50, color: '#1e9cbb'],
            [value: 60, color: '#40b2a0'],
            [value: 65, color: '#44b621'],
            [value: 70, color: '#92c711'],
            [value: 75, color: '#f1d801'],
            [value: 80, color: '#d04e00'],
            [value: 85, color: '#bc2323']
          ]
        )
      }
      tileAttribute('device.targetTemperature', key: 'VALUE_CONTROL', label: '${currentValue}°') {
        attributeState('up', action: 'setTargetTemperature')
        attributeState('down', action: 'setTargetTemperature')
      }
      tileAttribute ('device.operatingMode', key: 'SECONDARY_CONTROL') {
        attributeState('off', label: '${currentValue}', icon: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-off-120.png')
        attributeState('override', label: '${currentValue}', icon: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-override-120.png')
        attributeState('timer', label: '${currentValue}', icon: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-timer-120.png')
        attributeState('footprint', label: '${currentValue}', icon: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-footprint-120.png')
      }
    }
    standardTile('brand', 'device', width: 1, height: 1, decoration: 'flat') {
      state 'default', label: '', icon: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-60.png'
    }
    standardTile('refresh', 'device', width: 1, height: 1, decoration: 'flat') {
      state 'default', label: '', action: 'refresh', icon: 'st.secondary.refresh'
    }
    standardTile('extraHour', 'device.canModifyOverride', width: 1, height: 1, decoration: 'flat') {
      state 'default', label: null
      state 'yes', label: 'Extra hour', action: 'extraHour'
    }
    standardTile('revert', 'device.canModifyOverride', width: 1, height: 1, decoration: 'flat') {
      state 'default', label: null
      state 'yes', label: '', action: 'revert', icon: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-revert-120.png'
    }
    // Suspected SmartThings bugs:
    // 1. A battery value of 49 is coloured green. If an additional checkpoint of `[value: 48, color: '#f1d801']` is added, it's then coloured yellow.
    //    It's really not clear what's going on here. However, it _is_ clear from the way temperatures are treated wrt background colours that it's not
    //    the simple value comparison that the documentation suggests, and that debugging is basically impossible.
    // 2. The yellow colour is pretty greenish - nothing like the hex colour. It looks like it may be a mixture of the background colour of the
    //    multiattribute tile and the value tile.
    valueTile('battery', 'device.battery', inactiveLabel: false, width: 1, height: 1) {
      state 'battery', label: '${currentValue}% battery',
      backgroundColors:[
        [value: 10, color: '#bc2323'],
        [value: 26, color: '#f1d801'],
        [value: 51, color: '#44b621']
      ]
    }
    valueTile('illuminance', 'device.illuminance', inactiveLabel: false, width: 1, height: 1) {
      state 'illuminance', label: '${currentValue} lux'
    }
    valueTile('overrideEndTime', 'device.overrideEndTimeDisplay', width: 4, height: 1) {
      state 'default', label: '${currentValue}'
    }

    main(['thermostat'])
    details(['thermostat', 'brand', 'refresh', 'extraHour', 'revert', 'battery', 'illuminance', 'overrideEndTime'])
  }
}

//#region Methods called by parent app

/**
 * Stores the Genius Hub id of this room in state.
 *
 * @param geniusId  Id of the room zone within the Genius Hub.
 */
void setGeniusId(Integer geniusId) {
  state.geniusId = geniusId
}

/**
 * Stores the configured log level in state.
 *
 * @param logLevel  Configured log level.
 */
void setLogLevel(Integer logLevel) {
  state.logLevel = logLevel
}

/**
 * Returns the Genius Hub id of this room.
 */
Integer getGeniusId() {
  return state.geniusId
}

/**
 * Returns the type of this device.
 */
String getGeniusType() {
  return 'room'
}

/**
 * Updates the state of the room.
 *
 * @param values  Map of attribute names and values.
 */
void updateState(Map values) {
  logger "${device.label}: updateState: ${values}", 'trace'

  if (values?.containsKey('operatingMode')) {
    sendEvent(name: 'operatingMode', value: values.operatingMode)
  }

  if (values?.containsKey('sensorTemperature')) {
    def value = convertCelsiusToHubScale(values.sensorTemperature)
    sendEvent(name: 'temperature', value: value, unit: "°${temperatureScale}")    
  }

  if (values?.containsKey('minBattery')) {
    sendEvent(name: 'battery', value: values.minBattery, unit: '%')
  }

  if (values?.containsKey('illuminance')) {
    sendEvent(name: 'illuminance', value: values.illuminance, unit: 'lux')
  }
  
  if (values?.containsKey('overrideEndTime')) {
    sendEvent(name: 'overrideEndTime', value: values.overrideEndTime, displayed: false)
  }
  
  updateDisplay()
}

//#endregion Methods called by parent app

//#region Actions

/**
 * Not used in this device handler.
 */
def parse(String description) {
}

/**
 * Extend the override by an hour.
 */
def extraHour() {
  logger "${device.label}: refresh", 'trace'

  if (device.currentValue('operatingMode') == 'override') {
    def overrideEndTime = device.currentValue('overrideEndTime')
    def period = 3600
    if (overrideEndTime) {
      period = (int)(((overrideEndTime.getTime() + 3600 * 1000) - now()) / 1000)
    }

    parent.pushOverridePeriod(state.geniusId, period)
  }
}

/**
 * Refresh all devices.
 */
def refresh() {
  logger "${device.label}: refresh", 'trace'

  parent.refresh()
}

/**
 * Revert the operating mode to the default.
 */
def revert() {
  logger "${device.label}: revert", 'trace'
  
  if (device.currentValue('operatingMode') == 'override') {
    parent.revert(state.geniusId)
  }
}

/**
 * Sets the operating mode to override and the target temperature to the specified value.
 *
 * @param value  Target temperature, in either Celsius or Fahrenheit as defined by the SmartThings hub settings.
 */
def setTargetTemperature(Double value) {
  logger "${device.label}: setTargetTemperature: ${value}", 'trace'

  sendEvent(name: 'targetTemperature', value: value, unit: "°${temperatureScale}")  

  def valueInCelsius = convertHubScaleToCelsius(value)
  parent.pushRoomTemperatureAsync(state.geniusId, valueInCelsius)
}

//#endregion Actions

//#region Helpers

/**
 * Update the attributes used to determine how tiles are displayed,
 * based on state.
 */
private void updateDisplay() {
  logger "${device.label}: updateDisplay", 'trace'

  def operatingMode = device.currentValue('operatingMode')

  // Despite this being stored in state as a long (and it's definitely this
  // as storing a Date in state doesn't work) it's coming back here as a Date.
  def overrideEndTime = device.currentValue('overrideEndTime')

  if (operatingMode == 'override') {
    sendEvent(name: 'canModifyOverride', value: 'yes', displayed: false)
    if (overrideEndTime) {
      sendEvent(name: 'overrideEndTimeDisplay', value: "Override ends ${overrideEndTime.format('HH:mm')}", displayed: false)
    }
  } else {
    sendEvent(name: 'canModifyOverride', value: '', displayed: false)
    sendEvent(name: 'overrideEndTimeDisplay', value: '', displayed: false)
    sendEvent(name: 'targetTemperature', value: device.currentValue('temperature'), unit: "°${temperatureScale}", displayed: false)
  }
}

/**
 * Converts a Celsius temperature value to the scale defined in the SmartThings hub settings.
 *
 * @param valueInCelsius  Temperature in Celsius.
 */
private Double convertCelsiusToHubScale(Double valueInCelsius) {
  def value = (temperatureScale == "F") ? ((valueInCelsius * 1.8) + 32) : valueInCelsius
  return value.round(1)
}

/**
 * Converts a temperature value on the scale defined in the SmartThings hub settings to Celsius.
 *
 * @param valueInHubScale  Temperature in the unit defined in the SmartThings hub settings.
 */
private Double convertHubScaleToCelsius(Double valueInHubScale) {
  def value = (temperatureScale == "C") ? valueInHubScale : ((valueInHubScale - 32) / 1.8)
  return value.round(1)
}

/**
 * Log message if logging is configured for the specified level.
 */
private void logger(message, String level = 'debug') {
  switch (level) {
    case 'error':
      if (state.logLevel >= 1) log.error message
      break
    case 'warn':
      if (state.logLevel >= 2) log.warn message
      break
    case 'info':
      if (state.logLevel >= 3) log.info message
      break
    case 'debug':
      if (state.logLevel >= 4) log.debug message
      break
    case 'trace':
      if (state.logLevel >= 5) log.trace message
      break
    default:
      log.debug message
      break
  }
}

//#endregion Helpers
