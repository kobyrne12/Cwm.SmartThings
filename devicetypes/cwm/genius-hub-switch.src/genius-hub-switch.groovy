/**
 *  Genius Hub Switch
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
  definition (name: 'Genius Hub Switch', namespace: 'cwm', author: 'Neil Cumpstey', vid: 'generic-switch') {
    capability 'Actuator'
    capability 'Health Check'
    capability 'Switch'

    command 'extraHour'
    command 'refresh'
    command 'revert'

    attribute 'operatingState', 'enum', ['off', 'timer', 'override']
    attribute 'overrideEndTime', 'date'
  }

  preferences {
    section {
      input name: 'switchMode', type: 'enum', title: 'Switch mode', defaultValue: 'genius',
        options: [
          'genius' : 'Genius Hub switch (override for a period and revert)',
          'switch' : 'Normal on/off switch (permanent override)'
        ]
    }
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
      tileAttribute ('device.operatingState', key: 'SECONDARY_CONTROL') {
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
    valueTile('overrideEndTime', 'device.overrideEndTimeDisplay', width: 4, height: 1) {
      state 'default', label: '${currentValue}'
    }

    main(['switch'])
    details(['switch', 'brand', 'refresh', 'extraHour', 'revert', 'overrideEndTime'])
  }
}

//#region Event handlers

/**
 * Called when the settings are updated.
 */
def updated() {
  state.switchMode = settings.switchMode ?: 'genius'

  if (state.switchMode == 'switch') {
    parent.pushSwitchState(state.geniusId, device.currentValue('switch') == 'on')
    runEvery1Hour(extendOverride)
  } else {
    unschedule(extendOverride)
  }

  updateDisplay()
}

//#endregion Event handlers

//#region Methods called by parent app

/**
 * Stores the Genius Hub id of this switch in state.
 *
 * @param geniusId  Id of the switch zone within the Genius Hub.
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

  if (values?.containsKey('operatingState')) {
    sendEvent(name: 'operatingState', value: values.operatingState)
  }

  if (values?.containsKey('switchState')) {
    sendEvent(name: 'switch', value: (values.switchState ? 'on' : 'off'))
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
  logger "${device.label}: extraHour", 'trace'

  if (state.switchMode == 'genius' && device.currentValue('operatingState') == 'override') {
    def overrideEndTime = device.currentValue('overrideEndTime')
    def period = 3600
    if (overrideEndTime) {
      period = (int)(((overrideEndTime.getTime() + 3600 * 1000) - now()) / 1000)
    }

    parent.pushOverridePeriod(state.geniusId, period)
  }
}

/**
 * Turn on the switch.
 */
def off() {
  logger "${device.label}: off", 'trace'

  sendEvent(name: 'switch', value: 'turningOff', isStateChange: true)

  parent.pushSwitchState(state.geniusId, false)
}

/**
 * Turn on the switch.
 */
def on() {
  logger "${device.label}: on", 'trace'

  sendEvent(name: 'switch', value: 'turningOn', isStateChange: true)

  parent.pushSwitchState(state.geniusId, true)
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
  
  if (state.switchMode == 'genius' && device.currentValue('operatingState') == 'override') {
    parent.revert(state.geniusId)
  
    // The api reponse doesn't contain the state of the switch after the mode has changed,
    // And it takes a couple of seconds for this to be reliably updated.
    sendEvent(name: 'switch', value: 'refreshing', displayed: false)
    runIn(4, parent.refresh)
  }
}

//#endregion Actions

//#region Helpers

/**
 * Update the attributes used to determine how tiles are displayed,
 * based on switch mode and state.
 */
private void updateDisplay() {
  logger "${device.label}: updateDisplay", 'trace'

  def operatingState = device.currentValue('operatingState')

  def overrideEndTime = device.currentValue('overrideEndTime')

  if (state.switchMode == 'genius' && operatingState == 'override') {
    sendEvent(name: 'canModifyOverride', value: 'yes', displayed: false)
    if (overrideEndTime) {
      sendEvent(name: 'overrideEndTimeDisplay', value: "Override ends ${overrideEndTime.format('HH:mm')}", displayed: false)
    }
  } else {
    sendEvent(name: 'canModifyOverride', value: '', displayed: false)
    sendEvent(name: 'overrideEndTimeDisplay', value: '', displayed: false)
  }
}

/**
 * Set the override period to just over an hour.
 * For use by switch mode, where this is called every hour.
 */
private void extendOverride() {
  logger "${device.label}: extendOverride", 'trace'

  parent.pushOverridePeriod(state.geniusId, 3660)
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
