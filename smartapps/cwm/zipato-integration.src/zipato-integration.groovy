/**
 *  Zipato Integration
 * 
 *  Copyright 2018 Neil Cumpstey
 * 
 *  A SmartThings smart app which integrates with Zipato.
 *  It can be used to interact with devices on a Zipato box, such as LightwaveRF
 *  which is not directly compatible with a SmartThings hub.
 *
 *  ---
 *  Disclaimer: This smart app and the associated device handlers are in no way sanctioned or supported by Zipato.
 *  All development is based on Zipato's published API.
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
  name: 'Zipato Integration',
  namespace: 'cwm',
  author: 'Neil Cumpstey',
  description: 'Integrate Zipato devices with SmartThings.',
  iconUrl: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/zipato-integration.src/assets/zipato-60.png',
  iconX2Url: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/zipato-integration.src/assets/zipato-120.png',
  iconX3Url: 'https://raw.githubusercontent.com/cumpstey/Cwm.SmartThings/master/smartapps/cwm/zipato-integration.src/assets/zipato-340.png',
  singleInstance: true
)

private apiRootUrl() { return 'https://my.zipato.com/zipato-web/v2/' }
private jsonContentType() { return  'application/json' }
private sessionCookie() { return  'JSESSIONID' }

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
Error communicating with Zipato api:
${state.currentError}
Resolve the error if possible and try again.""" 
  }
  
  return dynamicPage(name: 'mainPage', title: null, install: true, uninstall: true) {
    if (error) {
      section {
        paragraph image: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png', "${error}"
      }
    }
    section ('Zipato settings') {
      href name: 'toAuthenticationPage', page: 'authenticationPage', title: "Authenticated as ${settings.zipatoUsername}", description: 'Tap to change' , state: state.authenticated ? 'complete' : null
    }
    section ('Devices') {
      href name: 'tomanageDevicesPage', page: 'manageDevicesPage', title: 'Add devices'
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
      input 'zipatoUsername', 'text', title: 'Username', required: true, displayDuringSetup: true
      input 'zipatoPassword', 'password', title: 'Password', required: true, displayDuringSetup: true
      input 'zipatoSerial', 'text', title: 'Zipabox serial number', description: 'Only needed if you have more than one Zipabox', required: false, displayDuringSetup: true
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
    ? 'Authentication successful. If you\'ve connected to a different account or different Zipabox, any existing devices may no longer work.'
    : 'Authentication successful. Save to complete installation, then come back into the settings to add devices.'
  return dynamicPage(name: 'authenticatedPage', title: 'Authenticated', install: state.installed ? false : true, nextPage: 'mainPage') {
    section {
      paragraph "${message}"
    }
  }
}

def manageDevicesPage() {
  // Get available devices from api
  fetchDevices()

  // If there's an error connecting to the api, return to main page to show it
  if (state.currentError != null) {
    return mainPage()
  }

  // Check if device selection has changed, and update child devices if it has
  def selectedDevicesKey = settings.selectedSwitches ? settings.selectedSwitches.sort(false).join(',') : null
  if (state.selectedDevicesKey != selectedDevicesKey) {
    updateChildDevices()
    state.selectedDevicesKey = selectedDevicesKey
  }

  // Generate options from list of available devices
  def switchOptions = [:]
  state.switches.each {
    switchOptions[it.key] = "${it.value?.device?.name} > ${it.value?.endpoint?.name} > ${it.value?.name}"
  }

  // Set empty list warning message
  def switchesMessage = null
  if (switchOptions == [:]) {
    switchesMessage = 'No switches available.'
  }

  return dynamicPage (name: "manageDevicesPage", title: "Select devices", install: false, uninstall: false) {
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
  runEvery5Minutes('refresh')

  // Reauthenticate every 3 hours
  runEvery3Hours('authenticate')
}

def updated() {
  logger "${app.label}: updated", 'trace'

  state.logLevel = settings.logging ? 5 : 2

  children.each {
    it.setLogLevel(state.logLevel)
  }
  
  unsubscribe()

  // Refresh data every 5 minutes
  runEvery5Minutes('refresh')

  // Reauthenticate every 3 hours
  runEvery3Hours('authenticate')
}

def uninstalled() {
  removeAllChildDevices()
}

//#endregion App event handlers

//#region Service manager functions

private authenticate() {
  logger "${app.label}: authenticate", 'trace'

  initialiseSession()
  if (!state.currentError) {
    login()
  }
}

private updateChildDevices() {
  logger "${app.label}: updateChildDevices", 'trace'

  // Remove child devices for unselected options
  def children = getChildDevices()
  children.each {
    def zipatoId = it.getZipatoId()
    if (!settings.selectedSwitches.contains(zipatoId)) {
      logger "Deleting device ${it.name} (${it.deviceNetworkId})"
      try {
        deleteChildDevice(it.deviceNetworkId)
      }
      catch(e) {
        logger "Error deleting device ${it.deviceNetworkId}: ${e}"
      }
    }
  }

  // Create child devices for selected options
  settings.selectedSwitches?.each {
    def isChild = getChildDevice("ZIPATO-${it}")
    if (!isChild) {
      def label = state.switches?.get(it)?.device?.name
      createChildDevice(label, 'Zipato Switch', it)
    }
  }
}

private createChildDevice(label, deviceType, zipatoId) {
  logger "${app.label}: createChildDevice", 'trace'

  def deviceId = "ZIPATO-${zipatoId}"
  logger "Creating ${deviceType} ${deviceId} with label '${label}'"

  def device = addChildDevice(app.namespace, deviceType, deviceId, null, [ 'label': label ])
  if (device) {
    log.debug "setting zipatoId ${zipatoId} and logLevel ${state.logLevel} on child device ${device}"
    device.setZipatoId(zipatoId)
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

//#region Zipato API: private functions

private initialiseSession() {
  logger "${app.label}: initialiseSession", 'trace'
  
  def requestParams = [
    uri: apiRootUrl(),
    path: 'user/init',
    contentType: jsonContentType(),
  ]

  try {
    httpGet(requestParams) { response ->
      logger "Response: ${response.status}; ${response.data}"

      if (response.status == 200 && response.data && response.data.success) {
        state.authenticated = false
        state.authSessionId = response.data.jsessionid
        state.authNonce = response.data.nonce
        state.currentError = null

        logger 'User init succeeded'
      }
      else {
        apiError(response.status, "Unexpected status code: ${response.status}")
      }
    }
  } catch (groovyx.net.http.HttpResponseException e) {
    apiError(e.statusCode, "${e}")
  }
}

private login() {
  logger "${app.label}: login", 'trace'

  def token = sha1(state.authNonce + sha1(settings.zipatoPassword))

  def requestParams = [
    uri: apiRootUrl(),
    path: 'user/login',
    query: [ 'username': settings.zipatoUsername, 'token': token, 'serial': settings.zipatoSerial ],
    contentType: jsonContentType(),
    headers: [
      'Cookie': "${sessionCookie()}=${state.authSessionId}"
    ],
  ]

  try {
    httpGet(requestParams) { response ->
      logger "Response: ${response.status}; ${response.data}"

      if (response.status == 200 && response.data && response.data.success) {
        state.authenticated = true
        state.authNonce = response.data.nonce
        state.currentError = null

        logger 'Login succeeded'
      }
      else {
        apiError(response.status, "${response.data?.error}")
      }
    }
  } catch (groovyx.net.http.HttpResponseException e) {
    apiError(e.statusCode, e.message)
  }
}

// TODO: move the filtering into a wrapper or something
private fetchDevices() {
  logger "${app.label}: fetchDevices", 'trace'
  
  def requestParams = [
    uri: apiRootUrl(),
    path: 'attributes/full',
    query: [
      full: true
    ],
    contentType: jsonContentType(),
    headers: [
      'Cookie': "${sessionCookie()}=${state.authSessionId}",
    ],
  ]

  try {
    httpGet(requestParams) { response ->
      logger "Response: ${response.status}; ${response.data}"

      if (response.status == 200 && response.data) {
        def switches = [:]
        response.data.each {
          if (it?.definition?.cluster == 'com.zipato.cluster.OnOff') {
            switches[it.uuid] = it
          }
        }
        
        logger "Switches found: ${switches}"
        state.switches = switches
      }
      else {
        apiError(response.status, "Unexpected status code: ${response.status}")
      }
    }
  } catch (groovyx.net.http.HttpResponseException e) {
    apiError(e.statusCode, e.message)
  }
}

private fetchAllValues() {
  logger "${app.label}: fetchAllValues", 'trace'
  
  def requestParams = [
    uri: apiRootUrl(),
    path: 'attributes/values',
    contentType: jsonContentType(),
    headers: [
      'Cookie': "${sessionCookie()}=${state.authSessionId}",
    ],
  ]

  asynchttp_v1.get('fetchAllValuesResponseHandler', requestParams, [ callback: callback ])
}

private fetchAllValuesResponseHandler(response, data) {
  if (response.hasError()) {
    logger "API error received: ${response.getErrorMessage()}"
    return
  }

  logger "Fetch all values: ${response.status}: ${response.data}"

  def attributeValues = [:]
  response.json.each {
    attributeValues[it.uuid] = it.value.value
  }
  
  state.attributeValues = attributeValues
  logger "Fetched attribute values: ${attributeValues}"

  updateChildren()
}

//#endregion: Zipato API: private functions

//#region Zipato API: methods called by child devices

void pushSwitchState(zipatoId, value) {
  logger "${app.label}: pushSwitchState", 'trace'

  def requestParams = [
    uri: apiRootUrl(),
    path: "attributes/${zipatoId}/value",
    contentType: jsonContentType(),
    body: [ 'value': value ],
    headers: [
      'Cookie': "${sessionCookie()}=${state.authSessionId}",
    ],
  ]

  asynchttp_v1.put('pushSwitchStateResponseHandler', requestParams, [ 'zipatoId': zipatoId, 'switchState': value ] )
}

private pushSwitchStateResponseHandler(response, data) { 
  if (response.hasError()) {
    logger "API error received: ${response.getErrorMessage()}"
    return
  }

  logger "Push switch state: ${response.status}: ${response.data}"

  def state = [ switchState: data.switchState ]

  def child = getChildDevice("ZIPATO-${data.zipatoId}")
  child.updateState(state)
}

//#endregion Zipato API: available to child devices

//#region Helpers: private

private updateChildren() {
  logger "${app.label}: updateChildren", 'trace'

  def children = getChildDevices()
  children.each {
    def zipatoId = it.getZipatoId()
    def zipatoType = it.getZipatoType()
    def attributeValue = state.attributeValues[zipatoId]

    logger "Updating child ${zipatoType} ${zipatoId} with value ${attributeValue}"

    switch (zipatoType) {
      case 'switch':
        it.updateState([
          switchState: "${attributeValue}".toBoolean(),
        ])
        break
    }
  }
}

private apiError(statusCode, message) {
  logger "Api error: ${statusCode}; ${message}", 'warn'
  
  state.currentError = "${message}"
  if (statusCode >= 300) {
    state.authenticated = false
  }
}

private sha1(String value) {
  def sha1 = java.security.MessageDigest.getInstance('SHA1')
  def digest  = sha1.digest(value.getBytes())
  return new BigInteger(1, digest).toString(16)
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

//#endregion Helpers: private

//#region Helpers: available to child devices

void refresh() {
  fetchAllValues()
}

//#endregion Helpers: available to child devices
