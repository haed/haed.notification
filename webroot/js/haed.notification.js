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
  
  
  var notify = function(baseURL, channelID, notification) {
    
    if (channels[baseURL] && channels[baseURL][channelID] && channels[baseURL][channelID].subscriptions) {
      
      var subscriptions = channels[baseURL][channelID].subscriptions;
      
      if (subscriptions[notification.type]) {
        
        var message = notification.message;
        try {
          // try to parse
          message = JSON.parse(notification.message);
        } catch (error) { /* maybe this is a simple text message */ }
        
        for (var i = 0; i < subscriptions[notification.type].length; i++) {
          if (subscriptions[notification.type][i]) {
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
            haed.notification.getChannel({ baseURL: _baseURL, channelID: channelID })
              .done(deferred.resolve)
              .fail(deferred.reject);
          })
        .fail(deferred.reject);
      
      return deferred.promise();
    }, 
    
    /**
     * @param {object} parameters
     * @option {string} (optional) baseURL
     * @option {string} channelID
     */
    getChannel: function(parameters) {
      
      return function(parameters) {
        
        var baseURL = parameters ? validateBaseURL(parameters.baseURL) : validateBaseURL(null);
        var channelID = parameters.channelID;
        
        channels[baseURL] = channels[baseURL] || {};
        if (channels[baseURL][channelID] === undefined) {
          
          channels[baseURL][channelID] = { deferred: new jQuery.Deferred() };
          channels[baseURL][channelID].channel = function(baseURL, channelID) {
            
              var callback = function(baseURL, channelID) {
                
                var lastResponseBody = "";
                var stream = "";
                
                return function(response) {
                  
                  if (response.state === "messageReceived" && response.responseBody && response.responseBody.length > 0) {
                    
                    stream += response.responseBody;
                    
  
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
                        
                        // finally cut stream and get next index
                        stream = stream.substring(idx + 1 + l);
                        idx = stream.indexOf(":");
                        
                        // notify listeners
                        if (notification) {
                          notify(baseURL, channelID, notification);
                        }
                        
                      } else {
                        idx = -1;
                      }
                    }
                  }
                };
              }(baseURL, channelID);
              
              
              jQuery.atmosphere.subscribe(baseURL + "notification/v1/openChannel?channelID=" + encodeURIComponent(channelID) + "&outputComments=true", null, {
                  
                  headers: {
                    "X-Cache-Serial": function(ajaxRequest, request, create, response) {
                      
                      if (response && response.headers["X-Cache-Serial"]) {
                        return response.headers["X-Cache-Serial"];
                      } else {
                        return null;
                      }
                    }
                  }, 
                  
                  
                  // some stress test settings
                  timeout: 1000 * 60 * 60, // 1 hour, server timeout must be lower
//                  timeout: 1000 * 1, // stress test: 1sec
//                  suspend: false, 
                  
                  callback: callback, 
                    
                    // TODO @haed [haed]: does not work properly, too much atmosphere issues (critical issue: https://github.com/Atmosphere/atmosphere/issues/87)
                  transport: 'long-polling', 
                  fallbackTransport: 'long-polling', 
                  
                  
                    // TODO @haed [haed]: check: ie does not support streaming, also fallback will be ignored ...
                    // TODO @haed [haed]: streaming over vodafone stick usb does not work (lost some packages)
                    // TODO @haed [haed]: streaming does not re-connect after server side suspend
//                    transport: 'streaming', 
//                    fallbackTransport: 'long-polling', 
                    
//                    transport: 'websocket', 
//                    fallbackTransport: 'long-polling', 
                    
                    
                    contentType: 'text/plain;charset=utf-8', 
                    maxRequest: Math.pow(2, 53)
                });
              
              
              var pingDeferred = null;
              var keepPinging = null;
              
              
              var instance = {
                
                getBaseURL: function() {
                  return baseURL;
                }, 
                
                getID: function() {
                  return channelID;
                }, 
                
                keepPinging: function() {
                  
                  if (keepPinging == null) {
                    
                    keepPinging = new function() {
                      
                      var doneCallbacks = new jQuery.Callbacks();
                      var failCallbacks = new jQuery.Callbacks();
                      
                      return {
                        
                        fireOnSuccess: function() {
                          doneCallbacks.fire();
                        }, 
                        
                        fireOnError: function(error) {
                          failCallbacks.fire(error);
                        },
                        
                        done: function(func) {
                          doneCallbacks.add(func);
                          return this;
                        }, 
                        
                        fail: function(func) {
                          failCallbacks.add(func);
                          return this;
                        }
                      };
                    }();
                    
                    var next = function(timeout) {
                        timeout = timeout || 60000; // initialize timeout to one minute
                        setTimeout(function(baseURL, channelID, keepPinging) {
                          return function() {
                              haed.notification.getChannel({ baseURL: baseURL, channelID: channelID })
                                .done(function(channel) {
                                    channel.ping()
                                      .done(function() {
                                          next();
                                          keepPinging.fireOnSuccess();
                                        })
                                      .fail(function(error) {
                                          next(10000); // ten seconds
                                          keepPinging.fireOnError(error);
                                        });
                                  });
                            };
                          }(baseURL, channelID, keepPinging), timeout);
                      };
                    
                    next(50);
                  }
                  
                  return keepPinging;
                }, 
                
                ping: function() {
                  
                  if (pingDeferred) {
                    return pingDeferred.promise();
                  }
                  
                  pingDeferred = new jQuery.Deferred();
                  pingDeferred.always(function() {
                      pingDeferred = null;
                    });
                  
                  jQuery.ajax(baseURL + "notification/v1/ping", {
                        data: { channelID: channelID }, 
                        type: "GET"
                      })
                    .fail(function(xmlHttpRequest, textStatus, error) {
                        if (pingDeferred) {
                          if (xmlHttpRequest.status === 0) {
                            // status 0 only occurs if the server is not available
                            pingDeferred.reject("noConnection");
                          } else {
                            // the server is available, but returned an error
                            // most likely the channel is gone here
                            // TODO [scthi]: handle channel gone
                            pingDeferred.reject("noPing");
                          }
                        }
                      });
                  
                  setTimeout(function(pingDeferred) {
                    return function() {
                      if (pingDeferred) {
                        pingDeferred.reject("noPing");
                      }
                    };
                  }(pingDeferred), 120000); // 2 minutes
                  
                  return pingDeferred.promise();
                }, 
                
                subscribe: function(notificationType, callback) {
                  
                  // TODO @haed subscriptions should be part of the channel instance
                  channels[baseURL][channelID].subscriptions = channels[baseURL][channelID].subscriptions || {};
                  var subscriptions = channels[baseURL][channelID].subscriptions;
                  
                  if (subscriptions[notificationType]) {
                    subscriptions[notificationType].push(callback);
                  } else {
                    subscriptions[notificationType] = [callback];
                  }
                }
              };
              
              // per default subscribe to ping (core notification api)
              instance.subscribe("haed.notification.ping." + channelID, function() {
                  
                  channels[baseURL][channelID].deferred.resolve(channels[baseURL][channelID].channel);
                  
                  if (pingDeferred) {
                    pingDeferred.resolve();
                  };
                });
              
              return instance;
              
            }(baseURL, channelID);
        }
        
        return channels[baseURL][channelID].deferred.promise();
      };
    }(), 
    
    getDefaultChannel: function(baseURL) {
      
      var _baseURL = validateBaseURL(baseURL);
      
      channels[_baseURL] = channels[_baseURL] || {};
      if (channels[_baseURL]["default"] === undefined) {
        
        channels[_baseURL]["default"] = { deferred: haed.notification.createChannel(_baseURL) };
      }
      
      return channels[_baseURL]["default"].deferred;
    }, 

    // TODO [scthi]: there should be a more convenient configure method
    setDefaultBaseURL: function(url) {
      defaultBaseURL = url;
    }
  };
  
  
  return instance;
  
}());