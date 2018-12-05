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
  iconX3Url: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/genius-hub-integration.src/assets/genius-hub-500.png',
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

  // Set up device options
  def options = [:]
  state.devices.each { key, value ->
    options[key] = value.name
  }

  // Set empty list warning message
  def message = null
  if (options == [:]) {
    message = 'No devices available.'
  }

  return dynamicPage (name: "manageDevicesPage", title: "Select devices", install: false, uninstall: false) {
    if (message) {
      section {
        paragraph "${message}"
      }
    }
    section {
      input name: 'selectedDevices', type: "enum", required: false, multiple: true,
            title: "Select devices (${options.size() ?: 0} found)", options: options //, submitOnChange: true
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
    state.logLevel = 5
  } else {
    state.logLevel = 2
  }

  updateChildDevices()

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

private void authenticate() {
  logger "${app.label}: authenticate", 'trace'

  verifyAuthentication()
}

private void updateChildDevices() {
  logger "${app.label}: updateChildDevices", 'trace'

  if (!settings.selectedDevices) {
    removeAllChildDevices()
    return
  }

  // Remove child devices for unselected options
  def children = getChildDevices()
  children.each {
    def geniusId = it.getGeniusId()

    def id = "id_${geniusId}"

    // `contains` doesn't work. I don't know why.
    if (settings.selectedDevices.disjoint([id])) {
      logger "Deleting device '${it.label}' (${geniusId})"
      try {
        deleteChildDevice(it.deviceNetworkId)
      }
      catch(e) {
        logger "Error deleting device '${it.label}' (${it.deviceNetworkId}): ${e}", 'error'
      }
    }
  }

  // Create child devices for selected options
  settings.selectedDevices.each {
    def geniusDevice = state.devices?.get(it)

    if (!geniusDevice) {
      logger "Inconsistent state: device ${it} selected but not found in Genius Hub devices", 'warn'
      return
    }

    createChildDevice(geniusDevice.type, geniusDevice.id, geniusDevice.name)
  }
}

private void createChildDevice(deviceType, geniusId, label) {
  logger "${app.label}: createChildDevice", 'trace'

  def deviceNetworkId = "GENIUS-${geniusId}"
  def child = getChildDevice(deviceNetworkId)
  if (child) {
    logger "Child device ${deviceNetworkId} already exists. Not creating."
    return
  }

  def deviceHandler = getDeviceHandlerFor(deviceType)

  logger "Creating ${deviceType} ${geniusId} with label '${label}' and device handler ${deviceHandler}"

  def device = addChildDevice(app.namespace, deviceHandler, deviceNetworkId, null, [ 'label': label ])
  logger "Device created: ${device}"
  if (device) {
    device.setGeniusId(geniusId)
    device.setLogLevel(state.logLevel)
  }
}

private void removeAllChildDevices(delete) {
  logger "${app.label}: removeAllChildDevices", 'trace'

  def devices = getChildDevices()
  devices.each {
    deleteChildDevice(it.deviceNetworkId)
  }
}

//#endregion Service manager functions

//#region Genius Hub API: private functions

/**
 * Make a request to the authentication test api endpoint to verify the credentials.
 */
private void verifyAuthentication() {
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

/**
 * Fetch information about all zones from the api, and store it in state.
 */
private void fetchZones(Closure callback = null) {
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
        logger "${response.data.data.size()} devices returned by the api"

        def devices = [:]
        response.data.data?.each {
          switch (it?.iType) {
            case 1:
              devices["id_${it.iID}"] = mapHouse(it)
              break;
            case 2:
              devices["id_${it.iID}"] = mapSwitch(it)
              break;
            case 3:
              devices["id_${it.iID}"] = mapRoom(it)
              break;
            default:
              logger "Unknown device type: ${it.iType} ${it.strName}", 'warn'
              break;
          }
        }
        
        logger "Found: ${devices.size()} devices"
        state.devices = devices

        if (callback) {
          callback()
        }
      }
      else {
        apiError(response.status, "Unexpected status code: ${response.status}")
      }
    }
  } catch (groovyx.net.http.HttpResponseException e) {
    apiError(e.statusCode, e.message)
  }
}

/**
 * Make a request to the api to set the mode of a zone.
 *
 * @param geniusId  Id of the zone within the Genius Hub.
 * @param mode  Mode which the zone should be switched to.
 */
private void pushMode(Integer geniusId, String mode) {
  logger "${app.label}: pushMode(${geniusId}, ${mode})", 'trace'

  def geniusMode = mapMode(mode)
  if (!geniusMode) {
    return
  }

  def requestParams = [
    uri: apiRootUrl(),
    path: "zone/${geniusId}",
    contentType: 'application/json',
    body: [
      'iMode': geniusMode
    ],
    headers: [
      'Authorization': getAuthorizationHeader()
    ],
  ]

  asynchttp_v1.patch('pushModeResponseHandler', requestParams, [ 'geniusId': geniusId ] )
}

/**
 * Handles the response from the request to the api to set the mode of a zone.
 *
 * @param response  Response.
 * @param data  Additional data passed from the calling method.
 */
private void pushModeResponseHandler(response, data) { 
  if (response.hasError()) {
    logger "API error received: ${response.getErrorMessage()}"
    return
  }

  logger "Push mode: ${response.json}"

  def updates = [:]
  def operatingMode = mapMode(response.json.data.iMode)
  if (operatingMode) {
    updates.operatingMode = operatingMode
  }

  def child = getChildDevice("GENIUS-${data.geniusId}")
  child.updateState(updates)
}

/**
 * Make a request to the api to set the override period of a zone.
 *
 * @param geniusId  Id of the zone within the Genius Hub.
 * @param period  Period in seconds.
 */
private void pushOverridePeriod(Integer geniusId, Integer period) {
  logger "${app.label}: pushOverridePeriod(${geniusId}, ${period})", 'trace'

  def requestParams = [
    uri: apiRootUrl(),
    path: "zone/${geniusId}",
    contentType: 'application/json',
    body: [
      'iBoostTimeRemaining': period
    ],
    headers: [
      'Authorization': getAuthorizationHeader()
    ],
  ]

  asynchttp_v1.patch('pushOverridePeriodResponseHandler', requestParams, [ 'geniusId': geniusId ] )
}

/**
 * Handles the response from the request to the api to set the override period of a zone.
 *
 * @param response  Response.
 * @param data  Additional data passed from the calling method.
 */
private void pushOverridePeriodResponseHandler(response, data) { 
  if (response.hasError()) {
    logger "API error received: ${response.getErrorMessage()}"
    return
  }

  logger "Push override period: ${response.json}"

  def updates = [:]

  // def overrideEndTime = null
  if (response.json.data.iBoostTimeRemaining) {
    updates.overrideEndTime = now() + response.json.data.iBoostTimeRemaining * 1000
  //   use(groovy.time.TimeCategory) {
  //     updates.overrideEndTime = new Date() + response.json.data.iBoostTimeRemaining.second 
  //   }
  }

  def child = getChildDevice("GENIUS-${data.geniusId}")
  child.updateState(updates)
}

//#endregion: Genius Hub API: private functions

//#region Genius Hub API: methods called by child devices

/**
 * Make a request to the api to override the room temperature.
 *
 * @param geniusId  Id of the room zone within the Genius Hub.
 * @param value  Temperature in Celcius.
 */
void pushRoomTemperature(Integer geniusId, Double value) {
  logger "${app.label}: pushRoomTemperature(${geniusId}, ${value})", 'trace'

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

  asynchttp_v1.patch('pushRoomTemperatureResponseHandler', requestParams, [geniusId: geniusId] )
}

/**
 * Handles the response from the request to the api to override the room temperature.
 *
 * @param response  Response.
 * @param data  Additional data passed from the calling method.
 */
private void pushRoomTemperatureResponseHandler(response, data) {
  if (response.hasError()) {
    logger "API error received: ${response.getErrorMessage()}"
    return
  }

  logger "Push room temperature: ${response}"

  def updates = [:]

  def operatingMode = mapMode(response.json.data.iMode)
  if (operatingMode) {
    updates.operatingMode = operatingMode
  }

  // def overrideEndTime = null
  if (response.json.data.iBoostTimeRemaining) {
    // use(groovy.time.TimeCategory) {
    //   updates.overrideEndTime = new Date() + response.json.data.iBoostTimeRemaining.second 
    // }
    updates.overrideEndTime = now() + response.json.data.iBoostTimeRemaining * 1000
  }

  def child = getChildDevice("GENIUS-${data.geniusId}")
  child.updateState(updates)
}

/**
 * Make a request to the api to override the state of a switch.
 *
 * @param geniusId  Id of the switch zone within the Genius Hub.
 * @param value  On/off state which the switch should be switched to.
 */
void pushSwitchState(Integer geniusId, Boolean value) {
  logger "${app.label}: pushSwitchState(${geniusId}, ${value})", 'trace'

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

/**
 * Handles the response from the request to the api to override the state of a switch.
 *
 * @param response  Response.
 * @param data  Additional data passed from the calling method.
 */
private void pushSwitchStateResponseHandler(response, data) { 
  if (response.hasError()) {
    logger "API error received: ${response.getErrorMessage()}"
    return
  }

  logger "Push switch state: ${response.json}"

  def updates = [ switchState: data.switchState ]

  def operatingMode = mapMode(response.json.data.iMode)
  if (operatingMode) {
    updates.operatingMode = operatingMode
  }

  // def overrideEndTime = null
  if (response.json.data.iBoostTimeRemaining) {
    // use(groovy.time.TimeCategory) {
    //   updates.overrideEndTime = new Date() + response.json.data.iBoostTimeRemaining.second 
    // log.debug "overrideEndTime: ${updates.overrideEndTime}"
    // log.debug "now: ${now()}"
    // log.debug "${updates.overrideEndTime.getTime()} ${now() + response.json.data.iBoostTimeRemaining * 1000}"
    // }
    updates.overrideEndTime = now() + response.json.data.iBoostTimeRemaining * 1000
  }

  def child = getChildDevice("GENIUS-${data.geniusId}")
  child.updateState(updates)
}

//#endregion Genius Hub API: available to child devices

//#region Helpers: private

private void apiError(statusCode, message) {
  logger "Api error: ${statusCode}; ${message}", 'warn'
  
  state.currentError = "${message}"
  if (statusCode >= 300) {
    state.authenticated = false
  }
}

private String getAuthorizationHeader() {
  def hash = sha256(settings.geniusHubUsername + settings.geniusHubPassword)
  def encoded = "${settings.geniusHubUsername}:${hash}".bytes.encodeBase64()
  return "Basic ${encoded}"
}

private String sha256(String value) {  
  def bytesOfPassword = value.getBytes("UTF-8");  
  def md = java.security.MessageDigest.getInstance("SHA-256");  
  md.update(bytesOfPassword);  
  def bytesOfEncryptedPassword = md.digest();  
  return new BigInteger(1, bytesOfEncryptedPassword).toString(16);
}  

private Map mapHouse(device) {
  return [
    id: device.iID,
    type: 'house',
    name: device.strName,
  ]
}

private Map mapRoom(device) {
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

  def operatingMode = mapMode(device.iMode)
  // def overrideEndTime = null
  // use(groovy.time.TimeCategory) {
  //   overrideEndTime = new Date() + device.iBoostTimeRemaining.second 
  // }
  def overrideEndTime = now() + device.iBoostTimeRemaining * 1000


  return [
    id: device.iID,
    type: 'room',
    name: device.strName,
    operatingMode: operatingMode,
    overrideEndTime: overrideEndTime,
    defaultOperatingMode: mapMode(device.iBaseMode),
    sensorTemperature: device.fPV,
    minBattery: minBattery,
    illuminance: illuminance,
    childNodes: children,
  ]
}

private Map mapSwitch(device) {
  def operatingMode = mapMode(device.iMode)
  // def overrideEndTime = null
  // use(groovy.time.TimeCategory) {
  //   overrideEndTime = new Date() + device.iBoostTimeRemaining.second 
  // }
  def overrideEndTime = now() + device.iBoostTimeRemaining * 1000


  return [
    id: device.iID,
    type: 'switch',
    name: device.strName,
    operatingMode: operatingMode,
    overrideEndTime: overrideEndTime,
    defaultOperatingMode: mapMode(device.iBaseMode),
    switchState: device.fSP.asBoolean(),
  ]
}

private mapMode(mode) {
  switch (mode) {
    case 1: return 'off'
    case 2: return 'timer'
    case 4: return 'footprint'
    case 16: return 'override'
    case 'off': return 1
    case 'timer': return 2
    case 'footprint': return 4
    case 'override': return 16
    default:
      if (mode != null) {
        logger "Unknown operating mode: ${mode}", 'warn'
      }

      return null
  }
}

private String getDeviceHandlerFor(String deviceType) {
  switch (deviceType) {
    case 'house':
      return 'Genius Hub House'
    case 'room':
      return 'Genius Hub Room'
    case 'switch':
      return 'Genius Hub Switch'
    default:
      return null
  }
}

private void logger(String message, String level = 'debug') {
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

//#endregion Helpers: private

//#region Helpers: available to child devices

/**
 * Refresh the data on all devices.
 */
void refresh() {
  logger "${app.label}: refresh", 'trace'

  fetchZones()
  
  def children = getChildDevices()
  children.each {
    def geniusId = it.getGeniusId()
    def geniusType = it.getGeniusType()
    def geniusDevice = state.devices["id_${geniusId}"]
    if (!geniusDevice) {
      logger "Child ${geniusType} ${geniusId} doesn't correspond to a zone on the Genius Hub", "warn"
      return
    }

    switch (geniusType) {
      case 'house':
        logger 'Not refreshing house - TODO'
        break;
      case 'room':
        it.updateState([
          operatingMode: geniusDevice.operatingMode,
          overrideEndTime: geniusDevice.overrideEndTime,
          sensorTemperature: geniusDevice.sensorTemperature,
          minBattery: geniusDevice.minBattery,
          illuminance: geniusDevice.illuminance,
        ])
        break;
      case 'switch':
        it.updateState([
          operatingMode: geniusDevice.operatingMode,
          overrideEndTime: geniusDevice.overrideEndTime,
          switchState: geniusDevice.switchState,
        ])
        break;
    }
  }
}

/**
 * Revert an overridden device to its base state.
 *
 * @param geniusId  Id of the zone within the Genius Hub.
 */
void revert(Integer geniusId) {
  logger "${app.label}: refresh", 'trace'

  fetchZones({
    def mode = state.devices["id_${geniusId}"].defaultOperatingMode
    pushMode(geniusId, mode)
  })
}

//#endregion Helpers: available to child devices
