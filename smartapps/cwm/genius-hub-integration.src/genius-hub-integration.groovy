/**
 *  Genius Hub Room
 * 
 *  Copyright 2018 Neil Cumpstey
 * 
 *  A SmartThings smart app which integrates with Genius Hub.
 *
 *  ---
 *  Disclaimer: This smart app and the associated device handlers are in no way sanctioned or supported by Genius Hub.
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
include 'asynchttp_v1'

definition(
  name: 'Genius Hub Integration',
  namespace: 'cwm',
  author: 'Neil Cumpstey',
  description: 'Integrate Genius Hub devices with SmartThings.',
  iconUrl: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-60.png',
  iconX2Url: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-120.png',
  iconX3Url: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-400.png',
  singleInstance: true
)

private apiRootUrl() { return 'https://hub-server-1.heatgenius.co.uk/v3/' }

//#region Preferences

preferences {
  page(name: 'mainPage')
  page(name: 'authenticationPage')
  page(name: 'authenticatedPage')
  page(name: 'manageDevicesPage')
}

def mainPage() {
  // If the app is not yet installed, first step is to authenticate
  if (!state.installed) {
    return authenticationPage(error)
  }

  // If there's an error connecting to the api, show it
  def error = null
  if (state.currentError != null) {
    error = """\
Error communicating with Genius Hub api:
${state.currentError}
Resolve the error if possible and try again.""" 
  }
  
  return dynamicPage(name: 'mainPage', title: null, install: true, uninstall: true) {
    if (error) {
      section {
        paragraph image: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png', "${error}"
      }
    }
    section ('Genius Hub settings') {
      href name: 'toAuthenticationPage', page: 'authenticationPage', title: "Authenticated as ${settings.geniusHubUsername}", description: 'Tap to change' , state: state.authenticated ? 'complete' : null
    }
    section ('Devices') {
      href name: 'tomanageDevicesPage', page: 'manageDevicesPage', title: 'Manage devices'
    }
    section('General') {
      input 'logging', 'bool', title: 'Debug logging', description: 'Enable logging of debug messages.'
      label title: 'Assign a name', required: false
    }
  }
}

def authenticationPage(params) {
  return dynamicPage(name: 'authenticationPage', title: 'Authentication', install: false, nextPage: 'authenticatedPage') {
    if (params?.error) {
        section {
          paragraph image: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png', "${params?.error}"
        }
    }
    section {
      input 'geniusHubUsername', 'text', title: 'Username', required: true, displayDuringSetup: true
      input 'geniusHubPassword', 'password', title: 'Password', required: true, displayDuringSetup: true
    }
  }
}

def authenticatedPage() {
  // Attempt authentication
  authenticate()
  
  // Return to authentication page with error if authentication fails
  if (!state.authenticated) {
    return authenticationPage('error': state.currentError)
  }

  def message = state.installed
    ? 'Authentication successful. If you\'ve connected to a different account, any existing devices may no longer work.'
    : 'Authentication successful. Save to complete installation, then come back into the settings to add devices.'
  return dynamicPage(name: 'authenticatedPage', title: 'Authenticated', install: state.installed ? false : true, nextPage: 'mainPage') {
    section {
      paragraph "${message}"
    }
  }
}

def manageDevicesPage() {
  // Get available devices from api
  fetchZones()

  // If there's an error connecting to the api, return to main page to show it
  if (state.currentError != null) {
    return mainPage()
  }

  // Check if device selection has changed, and update child devices if it has
  def selectedDevicesKey = settings.selectedHouses?.sort(false)?.join(',') + ';' +
                           settings.selectedRooms?.sort(false)?.join(',') + ';' +
                           settings.selectedSwitches?.sort(false)?.join(',')
  if (state.selectedDevicesKey != selectedDevicesKey) {
    updateChildDevices()
    state.selectedDevicesKey = selectedDevicesKey
  }

  // Generate options from list of available devices
  def houseOptions = [:]
  state.houses.each { key, value ->
    houseOptions[key] = value.name
  }

  def roomOptions = [:]
  state.rooms.each { key, value ->
    roomOptions[key] = value.name
  }

  def switchOptions = [:]
  state.switches.each { key, value ->
    switchOptions[key] = value.name
  }

  // Set empty list warning message
  def housesMessage = null
  if (houseOptions == [:]) {
    housesMessage = 'No houses available.'
  }
  
  def roomsMessage = null
  if (roomOptions == [:]) {
    roomsMessage = 'No rooms available.'
  }
  
  def switchesMessage = null
  if (switchOptions == [:]) {
    switchesMessage = 'No switches available.'
  }

  return dynamicPage (name: "manageDevicesPage", title: "Select devices", install: false, uninstall: false) {
    if (housesMessage) {
      section {
        paragraph "${housesMessage}"
      }
    }
    section {
      input name: 'selectedHouses', type: "enum", required: false, multiple: true,
            title: "Select houses (${houseOptions.size() ?: 0} found)", options: houseOptions, submitOnChange: true
    }
    if (roomsMessage) {
      section {
        paragraph "${roomsMessage}"
      }
    }
    section {
      input name: 'selectedRooms', type: "enum", required: false, multiple: true,
            title: "Select rooms (${roomOptions.size() ?: 0} found)", options: roomOptions, submitOnChange: true
    }
    if (switchesMessage) {
      section {
        paragraph "${switchesMessage}"
      }
    }
    section {
      input name: 'selectedSwitches', type: "enum", required: false, multiple: true,
            title: "Select switches (${switchOptions.size() ?: 0} found)", options: switchOptions, submitOnChange: true
    }
  }
}

//#endregion Preferences

//#region App event handlers

def installed() {
  state.installed = true

  // Refresh data every 5 minutes
  runEvery5Minutes(refresh)
}

def updated() {
  logger "${app.label}: updated", 'trace'

  if (settings.logging) {
    state.logLevel = 2
  }

  children.each {
    it.setLogLevel(state.logLevel)
  }

  unsubscribe()

  // Refresh data every 5 minutes
  runEvery5Minutes(refresh)
}

def uninstalled() {
  removeAllChildDevices()
}

//#endregion App event handlers

//#region Service manager functions

private authenticate() {
  logger "${app.label}: authenticate", 'trace'

  verifyAuthentication()
}

private updateChildDevices() {
  logger "${app.label}: updateChildDevices", 'trace'

  // Remove child devices for unselected options
  def children = getChildDevices()
  children.each {
    def geniusId = it.getGeniusId()
    def geniusType = it.getGeniusType()

    def delete = false;
    switch (geniusType) {
      case 'house':
        if (!settings.selectedHouses || !settings.selectedHouses.contains(geniusId)) { delete = true }
        break
      case 'room':
        if (!settings.selectedRooms || !settings.selectedRooms.contains(geniusId)) { delete = true }
        break
      case 'switch':
        if (!settings.selectedSwitches || !settings.selectedSwitches.contains(geniusId)) { delete = true }
        break
      default:
        logger "Unexpected Genius Hub device type for device ${geniusId}: ${geniusType}"
        delete = true
        break;
    }
    
    if (delete) {
      log.debug "Deleting device '${it.label}' (${it.deviceNetworkId})"
      try {
        deleteChildDevice(it.deviceNetworkId)
      }
      catch(e) {
        logger "Error deleting device '${it.label}' (${it.deviceNetworkId}): ${e}"
      }
    }
  }

  // Create child devices for selected options
  settings.selectedHouses?.each {  
    def geniusDevice = state.houses?.get(it)
    if (!geniusDevice) {
      logger "Inconsistent state: house ${it} selected but not found in Genius Hub devices", 'warn'
      return
    }

    createChildDevice('Genius Hub House', 'house', geniusDevice.id, geniusDevice.name)
  }
  
  settings.selectedRooms?.each {  
    def geniusDevice = state.rooms?.get(it)
    if (!geniusDevice) {
      logger "Inconsistent state: room ${it} selected but not found in Genius Hub devices", 'warn'
      return
    }

    createChildDevice('Genius Hub Room', 'room', geniusDevice.id, geniusDevice.name)
  }

  settings.selectedSwitches?.each {  
    def geniusDevice = state.switches?.get(it)
    if (!geniusDevice) {
      logger "Inconsistent state: switch ${it} selected but not found in Genius Hub devices", 'warn'
      return
    }

    createChildDevice('Genius Hub Switch', 'switch', geniusDevice.id, geniusDevice.name)
  }
}

private createChildDevice(deviceType, geniusType, geniusId, label) {
  logger "${app.label}: createChildDevice", 'trace'

  def deviceId = "GENIUS-${geniusId}"
  def child = getChildDevice(deviceId)
  if (child) {
    logger "Child device ${geniusId} already exists. Not creating."
    return
  }

  logger "Creating ${deviceType} ${deviceId} with label '${label}'"

  def device = addChildDevice(app.namespace, deviceType, deviceId, null, [ 'label': label ])
  if (device) {
    device.setGeniusId(geniusId)
    device.setLogLevel(state.logLevel)
  }
}

private removeAllChildDevices(delete) {
  logger "${app.label}: removeAllChildDevices", 'trace'

  def devices = getChildDevices()
  devices.each {
    deleteChildDevice(it.deviceNetworkId)
  }
}

//#endregion Service manager functions

//#region Genius Hub API: private functions

private verifyAuthentication() {
  logger "${app.label}: verifyAuthentication", 'trace'
  
  def requestParams = [
    uri: apiRootUrl(),
    path: 'auth/test',
    contentType: 'application/json',
    headers: [
      'Authorization': getAuthorizationHeader()
    ]
  ]

  try {
    httpGet(requestParams) { response ->
      logger "Response: ${response.status}; ${response.data}"

      if (response.status == 200) {
        state.authenticated = true
        state.currentError = null

        logger 'Authentication succeeded'
      }
      else {
        apiError(response.status, "Unexpected status code: ${response.status}")
      }
    }
  } catch (groovyx.net.http.HttpResponseException e) {
    apiError(e.statusCode, "${e}")
  }
}

private fetchZones() {
  logger "${app.label}: fetchZones", 'trace'
  
  def requestParams = [
    uri: apiRootUrl(),
    path: 'zones',
    contentType: 'application/json',
    headers: [
      'Authorization': getAuthorizationHeader()
    ],
  ]

  try {
    httpGet(requestParams) { response ->
      if (response.status == 200 && response.data) {
        def houses = [:]
        def rooms = [:]
        def switches = [:]
        log.debug "${response.data.data.size()} devices returned by the api"
        response.data.data?.each {
          switch (it?.iType) {
            case 1:
              houses["id_${it.iID}"] = mapHouse(it)
              break;
            case 2:
              switches["id_${it.iID}"] = mapSwitch(it)
              break;
            case 3:
              rooms["id_${it.iID}"] = mapRoom(it)
              break;
            default:
              logger "Unknown device type: ${it.iType} ${it.strName}"
              break;
          }
        }
        
        logger "Found: ${houses.size()} houses; ${rooms.size()} rooms; ${switches.size()} switches"
        state.houses = houses
        state.rooms = rooms
        state.switches = switches
        log.debug "Houses: ${houses}"
        log.debug "Rooms: ${rooms}"
        log.debug "Switches: ${switches}"
      }
      else {
        apiError(response.status, "Unexpected status code: ${response.status}")
      }
    }
  } catch (groovyx.net.http.HttpResponseException e) {
    apiError(e.statusCode, e.message)
  }
}

//#endregion: Genius Hub API: private functions

//#region Genius Hub API: methods called by child devices

void pushSwitchState(geniusId, value) {
  logger "${app.label}: pushSwitchState", 'trace'
  logger "Push switch state: ${geniusId}, ${value}"

  def requestParams = [
    uri: apiRootUrl(),
    path: "zone/${geniusId}",
    contentType: 'application/json',
    body: [
      'fBoostSP': value,
      'iBoostTimeRemaining': 3600,
      'iMode': 16
    ],
    headers: [
      'Authorization': getAuthorizationHeader()
    ],
  ]

  asynchttp_v1.patch('pushSwitchStateResponseHandler', requestParams, [ 'geniusId': geniusId, 'switchState': value ] )
}

private pushSwitchStateResponseHandler(response, data) { 
  if (response.hasError()) {
    logger "API error received: ${response.getErrorMessage()}"
    return
  }

  logger "Push switch state: ${response.json}"

  def state = [ switchState: data.switchState ]
  def operatingMode = mapMode(response.json.data.iMode)
  if (operatingMode) {
    state.operatingMode = operatingMode
  }

  def child = getChildDevice("GENIUS-${data.geniusId}")
  child.updateState(state)
}

def pushRoomTemperature(geniusId, value) {
  logger "${app.label}: pushRoomTemperature", 'trace'
  logger "Push room temperature: ${geniusId}, ${value}"

  def requestParams = [
    uri: apiRootUrl(),
    path: "zone/${geniusId}",
    contentType: 'application/json',
    body: [
      'fBoostSP': value,
      'iBoostTimeRemaining': 3600,
      'iMode': 16
    ],
    headers: [
      'Authorization': getAuthorizationHeader()
    ],
  ]

  asynchttp_v1.patch('pushRoomTemperatureResponseHandler', requestParams, ['geniusId': geniusId] )
}

def pushRoomTemperatureResponseHandler(response, data) {
  if (response.hasError()) {
    logger "API error received: ${response.getErrorMessage()}"
    return
  }

  logger "Push room temperature: ${response}"

  def state = [:]
  def operatingMode = mapMode(response.json.data.iMode)
  if (operatingMode) {
    state.operatingMode = operatingMode
  }

  def child = getChildDevice("GENIUS-${data.geniusId}")
  child.updateState(state)
}

//#endregion Genius Hub API: available to child devices

//#region Helpers: private

private apiError(statusCode, message) {
  logger "Api error: ${statusCode}; ${message}", 'warn'
  
  state.currentError = "${message}"
  if (statusCode >= 300) {
    state.authenticated = false
  }
}

private getAuthorizationHeader() {
  def hash = sha256(settings.geniusHubUsername + settings.geniusHubPassword)
  def encoded = "${settings.geniusHubUsername}:${hash}".bytes.encodeBase64()
  return "Basic ${encoded}"
}

private sha256(String value) {  
  def bytesOfPassword = value.getBytes("UTF-8");  
  def md = java.security.MessageDigest.getInstance("SHA-256");  
  md.update(bytesOfPassword);  
  def bytesOfEncryptedPassword = md.digest();  
  return new BigInteger(1, bytesOfEncryptedPassword).toString(16);
}  

private mapHouse(device) {
  return [
    id: device.iID,
    name: device.strName,
  ]
}

private mapSwitch(device) {
  return [
    id: device.iID,
    name: device.strName,
    operatingMode: mapMode(device.iMode),
    switchStatus: device.fSP,
  ]
}

private mapRoom(device) {
  def children = [:]
  device.nodes.each { 
    if (it.childValues.HEATING_1) {
      children["radiator_${it.addr}"] = [
        address: it.addr,
        type: 'radiator',
        battery: it.childValues.Battery.val,
      ]
    } else {
      children["sensor_${it.addr}"] = [
        address: it.addr,
        type: 'sensor',
        battery: it.childValues.Battery.val,
        illuminance: it.childValues.LUMINANCE.val,
        temperature: it.childValues.TEMPERATURE.val,
      ]
    }
  }

  def minBattery = children.collect{ it.value.battery }.min { it }

  def illuminance = children.findAll { it.value.type == 'sensor' }.collect{ it.value.illuminance }.max { it }

  return [
    id: device.iID,
    name: device.strName,
    operatingMode: mapMode(device.iMode),
    sensorTemperature: device.fPV,
    minBattery: minBattery,
    illuminance: illuminance,
    childNodes: children,
  ]
}

private mapMode(mode) {
  switch (mode) {
    case 1: return 'off'
    case 2: return 'timer'
    case 4: return 'footprint'
    case 16: return 'override'
    default:
      logger "Unknown operating mode: ${mode}"
      return null
  }
}

//#endregion Helpers: private

//#region Helpers: available to child devices

void refresh() {
  logger "${app.label}: refresh", 'trace'

  fetchZones()
  
  def children = getChildDevices()
  children.each {
    def geniusId = it.getGeniusId()
    def geniusType = it.getGeniusType()

    switch (geniusType) {
      case 'house':
        logger 'Not refreshing house - TODO'
        break;
      case 'room':
        def geniusDevice = state.rooms["id_${geniusId}"]
        if (geniusDevice) {
          it.updateState([
            operatingMode: geniusDevice.operatingMode,
            sensorTemperature: geniusDevice.sensorTemperature,
            minBattery: geniusDevice.minBattery,
            illuminance: geniusDevice.illuminance,
          ])
        }
        break;
      case 'switch':
        def geniusDevice = state.switches["id_${geniusId}"]
        if (geniusDevice) {
          it.updateState([
            operatingMode: geniusDevice.operatingMode,
            switchState: geniusDevice.switchState,
          ])
        }
        break;
    }
  }
}

void logger(msg, level = 'debug') {
  state.logLevel = 5
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

//#endregion Helpers: available to child devices
