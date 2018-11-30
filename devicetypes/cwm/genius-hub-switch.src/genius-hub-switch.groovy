/**
 *  Genius Hub Switch
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
  definition (name: 'Genius Hub Switch', namespace: 'cwm', author: 'Neil Cumpstey', vid: 'generic-switch') {
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
      state 'default', label: '', icon: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-60.png'
    }
    standardTile('refresh', 'device', width: 1, height: 1, decoration: 'flat') {
      state 'default', label: '', action: 'refresh', icon: 'st.secondary.refresh'
    }
    //controlTile("overrideTime", "device.time", "slider", height: 1, width: 6, inactiveLabel: true, range:"(1..20)") {
    //  state "default", action:"setTime"
    //}
    // standardTile('explicitOn', 'device.switch', width: 2, height: 1, decoration: 'flat') {
    //   state 'default', label: 'On', action: 'switch.on', icon: 'st.Home.home30', backgroundColor: '#ffffff'
    // }
    // standardTile('explicitOff', 'device.switch', width: 2, height: 1, decoration: 'flat') {
    //   state 'default', label: 'Off', action: 'switch.off', icon: 'st.Home.home30', backgroundColor: '#ffffff'
    // }

    main(['switch'])
    details(['switch', 'brand', 'refresh','overrideTime',  'explicitOn', 'explicitOff'])
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
  return 'switch'
}

void updateState(values) {
  logger "${device.label}: updateState: ${values}", 'trace'

  sendEvent(name: 'switch', value: (values.switchState ? 'on' : 'off'), isStateChange: true)
  // sendEvent(name: 'operatingMode', value: values.operatingMode, isStateChange: true)
}

//#endregion Methods called by parent app

//#region Actions

def parse(String description) {
}

def refresh() {
  logger "${device.label}: refresh", 'trace'
  parent.refresh()
}

def on() {
  logger "${device.label}: on", 'trace'

  sendEvent(name: 'switch', value: 'turningOn', isStateChange: true)

  parent.pushSwitchState(state.geniusId, 1)
}

def off() {
  logger "${device.label}: off", 'trace'

  sendEvent(name: 'switch', value: 'turningOff', isStateChange: true)

  parent.pushSwitchState(state.geniusId, 0)
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