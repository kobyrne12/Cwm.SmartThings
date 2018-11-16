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
  definition (name: 'Zipato Integrated Switch', namespace: 'cwm', author: 'Neil Cumpstey', vid: 'generic-switch') {
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
        attributeState 'on', label: '${name}', action: 'switch.off', icon: 'st.Home.home30', backgroundColor:'#00A0DC', nextState: 'turningOff'
        attributeState 'off', label: '${name}', action: 'switch.on', icon: 'st.Home.home30', backgroundColor:'#FFFFFF', nextState: 'turningOn', defaultState: true
        attributeState 'turningOn', label: 'Turning on', action: 'switch.off', icon: 'st.Home.home30', backgroundColor: '#00A0DC', nextState: 'turningOn'
        attributeState 'turningOff', label: 'Turning off', action: 'switch.on', icon: 'st.Home.home30', backgroundColor: '#FFFFFF', nextState: 'turningOff'
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

//#region Zipato API: methods called by parent app

void setState(values) {
  parent.logger "${device.label}: setState: ${values}", 'trace'
  
  if (values) {
    values.each { key, value ->
      if (['attributeId', 'logLevel'].contains(key)) {
        state."${key}" = value
      }
    }
  }
}

String getAttributeId() {
  parent.logger "${device.label}: getAttributeId", 'trace'

  return "${state.attributeId}"
}

void setValue(value) {
  parent.logger "${device.label}: setValue: ${value}", 'trace'

  def currentState = value?.toBoolean() ? 'on' : 'off'
  sendEvent(name: 'switch', value: currentState, isStateChange: true)
}

//#endregion Zipato API: methods called by parent app

def parse(String description) {
  parent.logger "${device.label}: parse: ${description}", 'trace'
}

def refresh() {
  parent.logger "${device.label}: refresh", 'trace'

  parent.refresh()
}

def on() {
  parent.logger "${device.label}: on", 'trace'

  sendEvent(name: 'switch', value: 'turningOn', isStateChange: true)

  if (parent.attribute_put_value(state.attributeId, 1)) {
    sendEvent(name: 'switch', value: 'on', isStateChange: true)
  } else {
    sendEvent(name: 'switch', value: 'off', isStateChange: true)
  }
}

def off() {
  parent.logger "${device.label}: off", 'trace'

  sendEvent(name: 'switch', value: 'turningOff', isStateChange: true)

  if (parent.attribute_put_value(state.attributeId, 0)) {
    sendEvent(name: 'switch', value: 'off', isStateChange: true)
  } else {
    sendEvent(name: 'switch', value: 'on', isStateChange: true)
  }
}
