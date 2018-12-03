/**
 *  Genius Hub Switch
 * 
 *  Copyright 2018 Neil Cumpstey
 * 
 *  A SmartThings device handler which wraps a device on a Genius Hub.
 *
 *  ---
 *  Disclaimer: This device handler and the associated smart app are in no way sanctioned or supported by Genius Hub.
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
  definition (name: 'Genius Hub Switch', namespace: 'cwm', author: 'Neil Cumpstey', vid: 'generic-switch') {
    capability 'Actuator'
    capability 'Switch'
    capability 'Health Check'

    command 'extraHour'
    command 'refresh'
    command 'revert'

    attribute 'operatingMode', 'string'
    attribute 'overrideEndTime', 'date'
    attribute 'overrideEndTimeDisplay', 'string'
  }

  preferences {
  }

  tiles(scale: 2) {
    multiAttributeTile(name:'switch', type: 'lighting', width: 6, height: 4, canChangeIcon: true) {
      tileAttribute ('device.switch', key: 'PRIMARY_CONTROL') {
        attributeState 'on', label: '${name}', action: 'switch.off', icon: 'st.Home.home30', backgroundColor:'#00a0dc', nextState: 'turningOff'
        attributeState 'off', label: '${name}', action: 'switch.on', icon: 'st.Home.home30', backgroundColor:'#ffffff', nextState: 'turningOn'
        attributeState 'turningOn', label: 'Turning on', action: 'switch.off', icon: 'st.Home.home30', backgroundColor: '#00a0dc', nextState: 'turningOn'
        attributeState 'turningOff', label: 'Turning off', action: 'switch.on', icon: 'st.Home.home30', backgroundColor: '#ffffff', nextState: 'turningOff'
        attributeState 'refreshing', label: 'Refreshing', action: 'switch.on', icon: 'st.Home.home30', backgroundColor: '#90bced', nextState: 'refreshing'
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
    standardTile('extraHour', 'device', width: 1, height: 1, decoration: 'flat') {
      state 'default', label: 'Extra hour', action: 'extraHour'
    }
    standardTile('revert', 'device.operatingMode', width: 1, height: 1, decoration: 'flat') {
      state 'default', label: ''
      state 'override', label: '', action: 'revert', icon: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-revert-120.png'
    }
    valueTile('overrideEndTime', 'device.overrideEndTimeDisplay', width: 4, height: 1) {
      state 'default', label: '${currentValue}'
    }

    main(['switch'])
    details(['switch', 'brand', 'refresh', 'extraHour', 'revert', 'overrideEndTime'])
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
  return 'switch'
}

/**
 * Updates the state of the switch.
 *
 * @param values  Map of attribute names and values.
 */
void updateState(Map values) {
  logger "${device.label}: updateState: ${values}", 'trace'

  if (values?.containsKey('operatingMode')) {
    sendEvent(name: 'operatingMode', value: values.operatingMode)
  }

  if (values?.containsKey('switchState')) {
    sendEvent(name: 'switch', value: (values.switchState ? 'on' : 'off'))
  }
  
  if (values?.containsKey('overrideEndTime')) {
    sendEvent(name: 'overrideEndTime', value: values.overrideEndTime, displayed: false)
  }
  
  def mode = device.currentValue('operatingMode')
  if (mode == 'override') {
    sendEvent(name: 'overrideEndTimeDisplay', value: "Override ends ${values.overrideEndTime.format("HH:mm")}", displayed: false)
  } else {
    sendEvent(name: 'overrideEndTimeDisplay', value: '', displayed: false)
  }
}

//#endregion Methods called by parent app

//#region Actions

/**
 * Not used in this device handler.
 * TODO: Can it be removed?
 */
def parse(String description) {
}

/**
 * Extend the override by an hour.
 */
void extraHour() {
  logger "${device.label}: refresh", 'trace'

  if (device.currentValue('operatingMode') == 'override') {
    logger "Not implemented", 'error'
    // parent.setOverridePeriod(state.geniusId, seconds)
  }
}

/**
 * Turn on the switch.
 */
void on() {
  logger "${device.label}: on", 'trace'

  sendEvent(name: 'switch', value: 'turningOn', isStateChange: true)

  parent.pushSwitchState(state.geniusId, true)
}

/**
 * Turn on the switch.
 */
void off() {
  logger "${device.label}: off", 'trace'

  sendEvent(name: 'switch', value: 'turningOff', isStateChange: true)

  parent.pushSwitchState(state.geniusId, false)
}

/**
 * Refresh all devices.
 */
void refresh() {
  logger "${device.label}: refresh", 'trace'

  parent.refresh()
}

/**
 * Revert the operating mode to the default.
 */
void revert() {
  logger "${device.label}: revert", 'trace'
  
  if (device.currentValue('operatingMode') == 'override') {
    parent.revert(state.geniusId)
  
    sendEvent(name: 'switch', value: 'refreshing', displayed: false)
    runIn(2, parent.refresh)
  }
}

//#endregion Actions

//#region Helpers

private void logger(msg, level = 'debug') {
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
