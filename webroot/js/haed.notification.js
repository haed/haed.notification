/* jslint instructions */
/*global haed, jQuery, window*/

"use strict";


if (window.haed === undefined) {
  window.haed = {};
}


haed.notification = (function() {
  
  // baseURL -> <channelID, { deferred, id, subscriptions }>
  var channels = {};
  
  var defaultBaseURL = "/";
  
  var pingDeferredCounter = 0;
  var pingDeferreds = {};
  
  var notify = function(baseURL, channelID, notification) {
    
    if (channels[baseURL] && channels[baseURL][channelID] && channels[baseURL][channelID].subscriptions) {
      
      var subscriptions = channels[baseURL][channelID].subscriptions;
      
      if (notification.type.indexOf("haed.notification.ping.") === 0) {
        try {
          var deferredID = JSON.parse(notification.message).deferredID;
          if (pingDeferreds[deferredID]) {
            pingDeferreds[deferredID].resolve();
          }
        } catch (error) { /* maybe this is a simple text message */ }
      }
      
      if (subscriptions[notification.type]) {
        for (var i = 0; i < subscriptions[notification.type].length; i++) {
          if (subscriptions[notification.type][i]) {

            var message = notification.message;
            try {
              // try to parse
              message = JSON.parse(notification.message);
            } catch (error) { /* maybe this is a simple text message */ }

            subscriptions[notification.type][i].call(this, message, notification.type);
          }
        }
      }
    }
  };
  
  var validateBaseURL = function(baseURL) {
    return baseURL || defaultBaseURL;
  };
  
  var instance = {
    
    createChannel: function(baseURL) {
      
      var deferred = new jQuery.Deferred();
      
      var _baseURL = validateBaseURL(baseURL);
      jQuery.get(_baseURL + "notification/v1/createChannel")
        .done(function(channelID) {
          deferred.resolve(haed.notification.getChannel({ baseURL: _baseURL, channelID: channelID }));
        })
        .fail(deferred.reject);
      
      return deferred.promise();
    }, 
    
    getDefaultChannelID: function(baseURL) {
      
      var _baseURL = validateBaseURL(baseURL);
      
      channels[_baseURL] = channels[_baseURL] || {};
      
      if (channels[_baseURL]["default"] === undefined) {
        channels[_baseURL]["default"] = { deferred: new jQuery.Deferred() };
        haed.notification.createChannel(_baseURL)
          .done(function(channelID) {
            channels[_baseURL][channelID] = channels[_baseURL]["default"];
            channels[_baseURL][channelID].id = channelID;
            haed.notification.openChannel(_baseURL, channelID);
          })
          .then(channels[_baseURL]["default"].deferred.resolve, channels[_baseURL]["default"].deferred.reject);
      }
      
      return channels[_baseURL]["default"].deferred.promise();
    }, 
    
    /**
     * @param {object} parameters
     * @option {string} (optional) baseURL
     * @option {string} (optional) channelID
     */
    getChannel: function(parameters) {
      
      return function(parameters) {
        
        var baseURL = parameters ? parameters.baseURL : null;
        haed.notification.openChannel(baseURL, parameters.channelID);
        
        return {
          
          getBaseURL: function() {
            return baseURL;
          }, 
          
          getID: function() {
            return parameters.channelID;
          }, 
          
          subscribe: function(notificationType, callback) {
            haed.notification.subscribe(baseURL, parameters.channelID, notificationType, callback);
          }
        } 
      };
    }(), 
    
    
    openChannel: function() {
      
      var createCallback = function(baseURL, channelID) {
        
        var lastResponseBody = "";
        var stream = "";
        
        return function(response) {
          
          // HOTFIX (for 0.7.2, maybe fixed in 0.8): atmosphere do not cut chunks out of the response body on polling (but in streaming it does)
          //  => so we have to do it manually
          if (response.responseBody.indexOf(lastResponseBody) === 0) {
            response.responseBody = response.responseBody.substring(lastResponseBody.length);
            lastResponseBody += response.responseBody;
          } else {
            lastResponseBody = response.responseBody;
          }
          
          // TODO @haed: remove (only debug)
          console.log("responseBody: " + response.responseBody);
          
          if (response.state === "messageReceived" && response.responseBody && response.responseBody.length > 0) {
            
            stream += response.responseBody;
            
            // TODO @haed: remove (only debug)
            console.log("stream: " + stream);

            var idx = stream.indexOf(":");
            while (idx > -1) {
              var l = parseInt(stream.substring(0, idx));
              if (stream.length > (l + idx)) {
                
                // parse notification
                var notification;
                try {
                  notification = JSON.parse(stream.substring(idx + 1, idx + 1 + l));
                } catch (error) {
                  // TODO: handle and log
                  console.log("error: " + error);
                }
                
                // notify listeners
                if (notification) {
                  notify(baseURL, channelID, notification);
                }
                
                // finally cut stream and get next index
                stream = stream.substring(idx + 1 + l);
                idx = stream.indexOf(":");
                
              } else {
                idx = -1;
              }
            }
          }
        };
      };
      
      return function(baseURL, channelID) {
        
        var _baseURL = validateBaseURL(baseURL);
        
        channels[_baseURL] = channels[_baseURL] || {};
        channels[_baseURL][channelID] = channels[_baseURL][channelID] || {};

        if (channels[_baseURL][channelID].open === undefined) {
          
          channels[_baseURL][channelID].id = channelID;
          channels[_baseURL][channelID].baseURL = _baseURL;
          
          channels[_baseURL][channelID].open = true;
          jQuery.atmosphere.subscribe(_baseURL + "notification/v1/openChannel?channelID=" + channelID + "&outputComments=true", createCallback(_baseURL, channelID), {
              
              transport: 'long-polling', 
//              fallbackTransport: 'long-polling', 
              
              // TODO [haed]: check: ie does not support streaming, also fallback will be ignored ...
              // TODO [haed]: streaming over vodafone stick usb does not work (lost some packages)
//              transport: 'streaming', 
//              fallbackTransport: 'long-polling', 
              
//              transport: 'websocket', 
//              fallbackTransport: 'polling', 
              
              
              
              contentType: 'text/plain;charset=utf-8', 
              maxRequest: Math.pow(2, 53)
            });
        }
        
        return channels[_baseURL][channelID];
      };
    }(), 
    
    ping: function(baseURL, channelID) {
      
      var deferredID = (++pingDeferredCounter);
      pingDeferreds[deferredID] = new jQuery.Deferred()
        .always(function() {
          delete pingDeferreds[deferredID];
        });
      
      jQuery.ajax(validateBaseURL(baseURL) + "notification/v1/ping", {
          data: {
            channelID: JSON.stringify(channelID), 
            message: JSON.stringify({ deferredID: deferredID })
          }, 
          type: "POST"
        })
        .error(pingDeferreds[deferredID].reject);
      
      setTimeout(function(deferred) {
        return function() {
          if (!deferred.isRejected() && !deferred.isResolved()) {
            deferred.reject();
          }
        };
      }(pingDeferreds[deferredID]), 30000);
      
      return pingDeferreds[deferredID].promise();
    },

    // TODO [scthi]: there should be a more convenient configure method
    setDefaultBaseURL: function(url) {
      defaultBaseURL = url;
    },
    
    subscribe: function(baseURL, channelID, notificationType, func) {
      
      var _baseURL = validateBaseURL(baseURL);
      
      channels[_baseURL] = channels[_baseURL] || {};
      channels[_baseURL][channelID] = channels[_baseURL][channelID] || {};
      channels[_baseURL][channelID].subscriptions = channels[_baseURL][channelID].subscriptions || {};
      
      var subscriptions = channels[_baseURL][channelID].subscriptions;
      if (subscriptions[notificationType]) {
        subscriptions[notificationType].push(func);
      } else {
        subscriptions[notificationType] = [func];
      }
    }
  };
  
  
  return instance;
  
}());