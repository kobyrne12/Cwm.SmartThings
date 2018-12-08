/**
 *  Zipato Switch
 * 
 *  Copyright 2018 Neil Cumpstey
 * 
 *  A SmartThings device handler which wraps a device on a Zipato box.
 *  It can be used to interact with devices on a Zipato box, such as LightwaveRF
 *  which is not directly compatible with a SmartThings hub.
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
  definition (name: 'Zipato Switch', namespace: 'cwm', author: 'Neil Cumpstey', vid: 'generic-switch') {
    capability 'Actuator'
    capability 'Switch'
    capability 'Health Check'

    command 'refresh'
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
      }
    }
    standardTile('brand', 'device', width: 1, height: 1, decoration: 'flat') {
      state 'default', label: '', icon: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/zipato-integration.src/assets/zipato-60.png'
    }
    standardTile('refresh', 'device', width: 1, height: 1, decoration: 'flat') {
      state 'default', label: '', action: 'refresh', icon: 'st.secondary.refresh'
    }
    standardTile('explicitOn', 'device.switch', width: 2, height: 1, decoration: 'flat') {
      state 'default', label: 'On', action: 'switch.on', icon: 'st.Home.home30', backgroundColor: '#ffffff'
    }
    standardTile('explicitOff', 'device.switch', width: 2, height: 1, decoration: 'flat') {
      state 'default', label: 'Off', action: 'switch.off', icon: 'st.Home.home30', backgroundColor: '#ffffff'
    }

    main(['switch'])
    details(['switch', 'brand', 'refresh', 'explicitOn', 'explicitOff'])
  }
}

//#region Methods called by parent app

/**
 * Stores the configured log level in state.
 *
 * @param logLevel  Configured log level.
 */
void setLogLevel(Integer logLevel) {
  state.logLevel = logLevel
}

/**
 * Stores the Zipato id of this switch in state.
 *
 * @param zipatoId  Id of the switch device within Zipato.
 */
void setZipatoId(String zipatoId) {
  log.debug ("setting zipato id ${zipatoId}")
  state.zipatoId = zipatoId
}

/**
 * Returns the Zipato id of this room.
 */
String getZipatoId() {
  return "${state.zipatoId}"
}

/**
 * Returns the type of this device.
 */
String getZipatoType() {
  return 'switch'
}

/**
 * Updates the state of the switch.
 *
 * @param values  Map of attribute names and values.
 */
void updateState(Map values) {
  logger "${device.label}: updateState: ${values}"

  if (values?.containsKey('switchState')) {
    sendEvent(name: 'switch', value: (values.switchState ? 'on' : 'off'))
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
 * Turn off the switch.
 */
def off() {
  logger "${device.label}: off", 'trace'

  sendEvent(name: 'switch', value: 'turningOff', isStateChange: true)

  parent.pushSwitchState(state.zipatoId, false)
}

/**
 * Turn on the switch.
 */
def on() {
  logger "${device.label}: on", 'trace'

  sendEvent(name: 'switch', value: 'turningOn', isStateChange: true)

  parent.pushSwitchState(state.zipatoId, true)
}

/**
 * Refresh all devices.
 */
def refresh() {
  logger "${device.label}: refresh", 'trace'

  parent.refresh()
}

//#endregion Actions

//#region Helpers

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

//#endregion Helpers
