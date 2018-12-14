/**
 *  Genius Hub House
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
  definition (name: 'Genius Hub House', namespace: 'cwm', author: 'Neil Cumpstey') {
    capability 'Actuator'
    capability 'Battery'
    capability 'Health Check'
    capability 'Refresh'
    capability 'Sensor'
    capability 'Temperature Measurement'
    capability 'Thermostat Heating Setpoint'

    command 'override'
    command 'refresh'
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
      tileAttribute('device.heatingSetpoint', key: 'VALUE_CONTROL', label: '${currentValue}°') {
        attributeState('up', action: 'setHeatingSetpoint')
        attributeState('down', action: 'setHeatingSetpoint')
      }
    }
    standardTile('brand', 'device', width: 1, height: 1, decoration: 'flat') {
      state 'default', label: '', icon: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-60.png'
    }
    standardTile('refresh', 'device', width: 1, height: 1, decoration: 'flat') {
      state 'default', label: '', action: 'refresh', icon: 'st.secondary.refresh'
    }
    standardTile('override', 'device', width: 1, height: 1, decoration: 'flat') {
      state 'default', label: 'Override', action: 'override', icon: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-override-120.png'
    }
    valueTile('battery', 'device.battery', inactiveLabel: false, width: 1, height: 1) {
      state 'battery', label: '${currentValue}% battery',
      backgroundColors:[
        [value: 10, color: '#bc2323'],
        [value: 26, color: '#f1d801'],
        [value: 51, color: '#44b621']
      ]
    }

    main(['thermostat'])
    details(['thermostat', 'brand', 'refresh', 'override', 'battery'])
  }
}

//#region Event handlers

/**
 * Called when the device is installed.
 */
def installed() {
  // Set the default target temperature to something sensible,
  // otherwise it'll be zero.
  sendEvent(name: 'heatingSetpoint', value: convertCelsiusToHubScale(21), unit: "°${temperatureScale}", displayed: false)
}

//#endregion Event handlers

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
  return 'house'
}

/**
 * Updates the state of the room.
 *
 * @param values  Map of attribute names and values.
 */
void updateState(Map values) {
  logger "${device.label}: updateState: ${values}", 'trace'

  if (values?.containsKey('sensorTemperature')) {
    def value = convertCelsiusToHubScale(values.sensorTemperature)
    sendEvent(name: 'temperature', value: value, unit: "°${temperatureScale}")    
  }

  if (values?.containsKey('minBattery')) {
    sendEvent(name: 'battery', value: values.minBattery, unit: '%')
  }
}

//#endregion Methods called by parent app

//#region Actions

/**
 * Not used in this device handler.
 */
def parse(String description) {
}

/**
 * Initiates override mode in all rooms in the house.
 */
def override() {
  logger "${device.label}: override", 'trace'

  def heatingSetpoint = device.currentValue('heatingSetpoint').toDouble()
  sendEvent(name: 'heatingSetpoint', value: heatingSetpoint, unit: "°${temperatureScale}", isStateChange: true)

  def heatingSetpointInCelsius = convertHubScaleToCelsius(heatingSetpoint)
  parent.pushHouseTemperature(heatingSetpointInCelsius)
}

/**
 * Refresh all devices.
 */
def refresh() {
  logger "${device.label}: refresh", 'trace'

  parent.refresh()
}

/**
 * Sets the operating mode to override and the target temperature to the specified value.
 *
 * @param value  Target temperature, in either Celsius or Fahrenheit as defined by the SmartThings hub settings.
 */
def setHeatingSetpoint(Double value) {
  logger "${device.label}: setHeatingSetpoint: ${value}", 'trace'

  sendEvent(name: 'heatingSetpoint', value: value, unit: "°${temperatureScale}", displayed: false)
}

//#endregion Actions

//#region Helpers

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
