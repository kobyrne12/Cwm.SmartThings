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
  page(name: 'newDevicePage')
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
Please resolve the error and try again.""" 
  }
  
  return dynamicPage(name: 'mainPage', title: null, install: true, uninstall: true) {
    if (error) {
      section {
        paragraph image: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png', "${error}"
      }
    }
    section ('Zipato settings') {
      href name: 'toAuthenticationPage', page: 'authenticationPage', title: "Authenticated as ${zipatoUsername}", description: 'Tap to change' , state: state.authenticated ? 'complete' : null
    }
    section ('Devices') {
      href name: 'toNewDevicePage', page: 'newDevicePage', title: 'Add devices'
    }
    section('General') {
      input (name: 'logLevel', title: 'Log level', type: 'enum',
        options: [
          '0': 'None',
          '1': 'Error',
          '2': 'Warning',
          '3': 'Info',
          '4': 'Debug',
          '5': 'Trace'
        ],
      )
      label title: "Assign a name", required: false
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

def newDevicePage() {
  // Get available devices from api
  attributes_get_valid()

  // If there's an error connecting to the api, return to main page to show it
  if (state.currentError != null) {
    return mainPage()
  }

  // Check if device selection has changed, and update child devices if it has
  def selectedDevicesKey = settings.selectedDevices ? settings.selectedDevices.sort(false).join(',') : null
  if (state.selectedDevicesKey != selectedDevicesKey) {
    updateChildDevices()
    state.selectedDevicesKey = selectedDevicesKey
  }

  // Generate options from list of available devices
  def options = [:]
  state.devices.each {
    options[it.key] = "${it.value?.device?.name} > ${it.value?.endpoint?.name} > ${it.value?.name}"
  }

  // Set empty list warning message
  def message = null
  if (options == [:]) {
    message = 'No devices available.'
  }

  return dynamicPage (name: "newDevicePage", title: "Select devices", install: false, uninstall: false) {
    if (message) {
      section {
        paragraph "${message}"
      }
    }
    section {
      input name: 'selectedDevices', type: "enum", required: false, multiple: true,
            title: "Select devices (${options.size() ?: 0} found)", options: options, submitOnChange: true
    }
  }
}

//#endregion Preferences

//#region App event handlers

def installed() {
  state.installed = true

  // Refresh data every 5 minutes
  runEvery5Minutes(refresh)

  // Reauthenticate every 3 hours
  runEvery3Hours(authenticate)
}

def uninstalled() {
  removeAllChildDevices()
}

//#endregion App event handlers

//#region Service manager functions

private authenticate() {
  logger "${app.label}: authenticate", 'trace'

  user_init()
  if (!state.currentError) {
    user_login()
  }
}

private updateChildDevices() {
  // Remove child devices for unselected options
  def children = getChildDevices()
  children.each {
    log.debug "Child device ${it.getAttributeId()}"
    if (!settings.selectedDevices.contains(it.deviceNetworkId.replace('ZIP-', ''))) {
      log.debug "Deleting device ${it.name} (${it.deviceNetworkId})"
      try {
        deleteChildDevice(it.deviceNetworkId)
      }
      catch(e) {
        logger "Error deleting device ${it.deviceNetworkId}: ${e}"
      }
    }
  }

  // Create child devices for selected options
  settings.selectedDevices?.each {
    def isChild = getChildDevice("ZIP-${it}")
    if (!isChild) {
      def label = state.devices?.get(it)?.device?.name
      createChildDevice(label, 'Zipato Integrated Switch', it)
    }
  }
}

private createChildDevice(label, deviceType, attributeId) {
  def deviceId = "ZIP-${attributeId}"
  log.debug "Creating ${deviceType} ${deviceId} with label '${label}'"

  def device = addChildDevice(app.namespace, deviceType, deviceId, null, [ 'label': label ])
  if (device) {
    device.setState(['attributeId': attributeId])
  }
}

private removeAllChildDevices(delete) {
  def devices = getChildDevices()
  devices.each {
    deleteChildDevice(it.deviceNetworkId)
  }
}

//#endregion Service manager functions

//#region Zipato API: private functions

private user_init() {
  logger "${app.label}: user_init", 'trace'
  
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

private user_login() {
  logger "${app.label}: user_login", 'trace'

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
private attributes_get_valid() {
  logger "${app.label}: attributes_get_valid()", 'trace'
  
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
        def devices = [:]
        response.data.each {
          if (it?.definition?.cluster == 'com.zipato.cluster.OnOff') {
            devices[it.uuid] = it
          }
        }
        
        logger "Devices found: ${devices}"
        state.devices = devices
      }
      else {
        apiError(response.status, "Unexpected status code: ${response.status}")
      }
    }
  } catch (groovyx.net.http.HttpResponseException e) {
    apiError(e.statusCode, e.message)
  }
}

private attributes_get_values() {
  logger "${app.label}: attributes_get_valid()", 'trace'
  
  def requestParams = [
    uri: apiRootUrl(),
    path: 'attributes/values',
    contentType: jsonContentType(),
    headers: [
      'Cookie': "${sessionCookie()}=${state.authSessionId}",
    ],
  ]

  try {
    httpGet(requestParams) { response ->
      logger "Response: ${response.status}; ${response.data}"

      if (response.status == 200 && response.data) {
        def deviceValues = [:]
        response.data.each {
          deviceValues[it.uuid] = it
        }
        
        state.deviceValues = deviceValues
      }
      else {
        apiError(response.status, "Unexpected status code: ${response.status}")
      }
    }
  } catch (groovyx.net.http.HttpResponseException e) {
    apiError(e.statusCode, e.message)
  }
}

//#endregion: Zipato API: private functions

//#region Zipato API: methods called by child devices

boolean attribute_put_value(attributeId, value) {
  logger "${app.label}: attribute_put_value", 'trace'

  def requestParams = [
    uri: apiRootUrl(),
    path: "attributes/${attributeId}/value",
    contentType: jsonContentType(),
    body: [ 'value': value ],
    headers: [
      'Cookie': "${sessionCookie()}=${state.authSessionId}",
    ],
  ]

  try {
    httpPutJson(requestParams) { response ->
      if (response.status == 202) {
        logger "Attribute updated: ${attribute} ${value}"
        return true
      }
      else {
        apiError(response.status, "Unexpected status code: ${response.status}")
        return false
      }
    }
  } catch (groovyx.net.http.HttpResponseException e) {
    apiError(e.statusCode, e.message)
    return false
  }
}

//#endregion Zipato API: available to child devices

//#region Helpers: private

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

//#endregion Helpers: private

//#region Helpers: available to child devices

void refresh() {
  attributes_get_values()
  
  def children = getChildDevices()
  children.each {
    def attributeId = it.getAttributeId()
    def value = state.deviceValues?.get(attributeId)?.value.value
    log.debug "Value for ${it}: ${value}"
    it.setValue(value)
  }
}

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

//#endregion Helpers: available to child devices
