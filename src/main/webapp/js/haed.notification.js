(function() {

  'use strict';

  var DEBUG = false;

  // polyfill for bind, from: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Function/bind
  if (!Function.prototype.bind) {
    Function.prototype.bind = function (oThis) {
      if (typeof this !== 'function') {
        // closest thing possible to the ECMAScript 5 internal IsCallable function
        throw new TypeError('Function.prototype.bind - what is trying to be bound is not callable');
      }

      /*jshint newcap: false */
      var aArgs = Array.prototype.slice.call(arguments, 1),
          fToBind = this,
          fNOP = function () {},
          fBound = function () {
            return fToBind.apply(this instanceof fNOP && (oThis ? this : oThis, aArgs.concat(Array.prototype.slice.call(arguments))));
          };

      fNOP.prototype = this.prototype;
      fBound.prototype = new fNOP();

      return fBound;
    };
  }

  // helper function to create a deferred
  var createDeferred = function() {
    return window.jQuery ? new window.jQuery.Deferred() : new window.Deferred();
  };

  // helper function to make ajax calls
  var call = function(type, url, sessionID, parameters, deferred) {

    deferred = deferred || createDeferred();

    var dataStr = [];
    if (parameters) {
      for (var key in parameters) {
        var value = JSON.stringify(parameters[key]);
        if (value) {
          dataStr.push(key + '=' + window.encodeURIComponent(value));
        }
      }
    }

    if (sessionID) {
      url += ';sessionID=' + sessionID;
    }

    var xhr = new XMLHttpRequest();

    xhr.open(type, url + (type === 'GET' && dataStr.length > 0 ? '?' + dataStr.join('&') : ''), true);
    if (dataStr.length > 0) {
      xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
    }

    xhr.onreadystatechange = function onreadystatechange() {

      if (xhr.readyState === 4) {

        if (xhr.status === 200) {

          if (xhr.responseText && xhr.responseText.length > 0 && xhr.getResponseHeader('Content-Type').indexOf('json') !== -1) {
            deferred.resolve(JSON.parse(xhr.responseText));
          }

          deferred.resolve(xhr.responseText);
        } else {
          deferred.reject(xhr);
        }

      } else if (xhr.readyState === 0) {
        deferred.reject(xhr);
      }

    };

    xhr.send(type === 'POST' && dataStr ? dataStr.join('&') : undefined);

    return deferred;

  };

  /**
   * @param baseURL
   * @param sessionID
   * @constructor
   */
  function NotificationCenter(baseURL, sessionID) {

    if (DEBUG) { console.log('new NotificationCenter', baseURL); }

    this.baseURL = baseURL || '/';
    this.sessionID = sessionID;

    this._channelDeferred = null;
    this.subscribersByTopic = {};

    this._lastMessageID = -1;
    this._pingHandler = null;
    this._errorListeners = [];

  }

  NotificationCenter.prototype = {

    getOrCreateChannel: function() {

      if (!this._channelDeferred || this._channelDeferred.state() === 'rejected') {

        var onSuccess = this._createChannelDone.bind(this);
        var onError = this._createChannelFailed.bind(this);

        this._channelDeferred = call('GET', this.baseURL + 'notification/v1/createChannel', this.sessionID)
            .then(onSuccess, onError);

      }

      return this._channelDeferred;
    },

    onMessage: function(callback, topic) {

      var subscribers = this.subscribersByTopic[topic];
      if (!subscribers) {
        this.subscribersByTopic[topic] = [ callback ];
      } else if (subscribers.indexOf(callback) === -1) {
        subscribers.push(callback);
      }
    },

    onError: function(callback) {

      if (callback) {
        this._errorListeners.push(callback);
      }
    },

    notify: function(message, topic, messageID) {

      if (DEBUG) { console.log('notify', message, topic, messageID); }

      if (typeof(messageID) === 'number') {

        // check if we missed a message
        if (this._lastMessageID !== -1 && this._lastMessageID + 1 !== messageID) {
          var msg = 'We missed a message, lastID:' + this._lastMessageID + ', current: ' + messageID;
          console.error(msg);
          this._informErrorListeners(new NotificationError('message_missed', msg));
        }

        this._lastMessageID = messageID;

      }

      var subscribers = this.subscribersByTopic[topic];

      if (!subscribers || subscribers.length === 0) {
        if (DEBUG) { console.log('No subscribers for', topic, ' -> ignored', message); }
        return false;
      }

      for (var i=0; i<subscribers.length; i++) {
        subscribers[i](message, topic);
      }

      return true;

    },

    getPingHandler: function() {
      this._pingHandler = this._pingHandler || new PingHandler(this);
      return this._pingHandler;
    },

    enablePing: function() {
      this.getPingHandler().start();
    },

    disablePing: function() {

      if (this._pingHandler) {
        this._pingHandler.stop();
      }

    },

    _createChannelDone: function(channelID) {
      return new Channel(channelID, this).init();
    },

    _createChannelFailed: function(xhr) {

      var msg = 'Unable to create channel: ' + xhr.responseText;
      if (DEBUG) { console.error(msg); }
      var error = new NotificationError('failed_to_create_channel', msg, xhr);
      this._informErrorListeners(error);

      return error;
    },

    _informErrorListeners: function(error) {

      for (var i=0; i<this._errorListeners.length; i++) {
        this._errorListeners[i](error);
      }
    }
  };

  /**
   * @param channelID
   * @param notificationCenter
   * @constructor
   */
  function Channel(channelID, notificationCenter) {

    if (DEBUG) { console.log('new Channel:', channelID, '@', notificationCenter.baseURL); }

    this.channelID = channelID;
    this.notificationCenter = notificationCenter;

    // config for atmosphere plugin
    this.url = notificationCenter.baseURL + 'notification/v1/openChannel?channelID=' + window.encodeURIComponent(channelID);
    this.contentType = 'application/json';
    this.fallbackTransport = 'long-polling';
    this.enableXDR = true;
    this.logLevel = DEBUG ? 'debug' : 'info';
    this.maxReconnectOnClose = Math.pow(2, 53);
//    this.suspend = false;
//    this.timeout = 1000 * 60 * 60; // 1 hour, server timeout must be lower
//    this.timeout = 1000 * 1; // stress test: 1sec
    this.transport = 'websocket';
//    this.transport = 'long-polling';
    
    this.trackMessageLength=true;

    this._initDeferred = null;

    this.onOpen = this.onOpen.bind(this);
    this.onError = this.onError.bind(this);

//    this._stream = '';
  }

  Channel.prototype = {

    init: function() {

      if (!this._initDeferred || this._initDeferred.state() === 'rejected') {
        this._initDeferred = createDeferred();

        var atmosphere = window.atmosphere || window.jQuery.atmosphere;
        atmosphere.subscribe(this);
      }

      return this._initDeferred;
    },

    onOpen: function(response) {

      if (DEBUG) { console.log('Channel.onOpen', response); }
      this._initDeferred.resolve(this);
      return response;
    },

    onError: function(cause) {

      var msg = 'Unable to open channel: ' + cause.reasonPhrase;
      console.error(msg);

      this._initDeferred.reject(this);

      var error = new NotificationError('open_channel_failed', msg, cause);
      this.notificationCenter._informErrorListeners(error);

      return error;
    },

    onMessage: function(response) {

      if (response.state !== 'messageReceived' ||  !response.responseBody || response.responseBody.length === 0) {
        if (DEBUG) { console.log('channel.onMessage - ignoring', response); }
        return;
      }

      if (DEBUG) { console.log('Channel.onMessage', response); }
      
      
      var notification;
      try {
        notification = JSON.parse(response.responseBody);
      } catch (error) {
        console.error('Unable to parse message: ' + error);
      }
      
      if (notification) {
        this.notificationCenter.notify(notification.message, notification.type, notification.id);
      }
      
//      this._stream += response.responseBody;
//
//      var idx = this._stream.indexOf(':');
//      while (idx > -1) {
//        var l = parseInt(this._stream.substring(0, idx), 10);
//        if (this._stream.length > (l + idx)) {
//
//          // parse notification
//          var notification;
//          try {
//            notification = JSON.parse(this._stream.substring(idx + 1, idx + 1 + l));
//          } catch (error) {
//            console.error('Unable to parse message: ' + error);
//          }
//
//          // finally cut stream and get next index
//          this._stream = this._stream.substring(idx + 1 + l);
//          idx = this._stream.indexOf(':');
//
//          // notify listeners
//          if (notification) {
//
//            if (notification.type.indexOf('ping') !== -1) {
//              notification.type = 'ping';
//            }
//
//            this.notificationCenter.notify(notification.message, notification.type, notification.id);
//          }
//
//        } else {
//          idx = -1;
//        }
//      }
    }
  };

  /**
   * @param notificationCenter
   * @constructor
   */
  function PingHandler(notificationCenter) {
    this.notificationCenter = notificationCenter;
    this.running = false;
    if (DEBUG) { console.log('new PingHandler', this.notificationCenter.baseURL); }

    // timestamps
    this.tsCallStarted = -1;
    this.tsCallDone    = -1;
    this.tsNotificationReceived = -1;

    this._ongoingPingCall = null;
    this._timerID = null;

    this._makePingCall = this._makePingCall.bind(this);
    this._pingCallDone = this._pingCallDone.bind(this);
    this._pingCallFailed = this._pingCallFailed.bind(this);
    this._onPingNotification = this._onPingNotification.bind(this);
    this._run = this._run.bind(this);

    this._subscribedChannels = [];

  }

  PingHandler.prototype = {

    start: function() {

      if (this.running) {
        return;
      }

      if (DEBUG) { console.log('PingHandler.start', this.notificationCenter.baseURL); }
      this.running = true;
      this._timerID = setInterval(this._run, 30000);

    },

    stop: function() {

      if (!this.running) {
        return;
      }

      this.running = false;
      clearInterval(this._timerID);

      if (DEBUG) { console.log('PingHandler.stop', this.notificationCenter.baseURL); }

    },

    sendPing: function() {

      if (!this._ongoingPingCall || this._ongoingPingCall.state() !== 'pending') {

        this._ongoingPingCall = createDeferred();

        this.notificationCenter.getOrCreateChannel()
            .then(this._makePingCall);
      }

      return this._ongoingPingCall;
    },

    _makePingCall: function(channel) {

      if (this._subscribedChannels.indexOf(channel.channelID) === -1) {
        this._subscribedChannels.push(channel.channelID);
        this.notificationCenter.onMessage(this._onPingNotification, 'haed.notification.ping.' + channel.channelID);
      }

      this.tsCallStarted = Date.now();
      this.tsCallDone = -1;
      this.tsNotificationReceived = -1;

      return call('GET', this.notificationCenter.baseURL + 'notification/v1/ping?channelID=' + encodeURIComponent(channel.channelID))
          .then(this._pingCallDone, this._pingCallFailed);

    },

    _pingCallDone: function(response) {

      this.tsCallDone = Date.now();
      if (DEBUG) { console.log('ping call took', (this.tsCallDone - this.tsCallStarted), 'ms'); }

      // nothing to do
      return response;
    },

    _pingCallFailed: function(xhr) {

      var msg = 'Unable to send ping to server: ' + xhr.responseText;
      if (DEBUG) { console.error(msg); }
      var error = new NotificationError('ping_call_failed', msg, xhr);
      this.notificationCenter._informErrorListeners(error);

      return error;
    },

    _onPingNotification: function(message, topic) {

      this.tsNotificationReceived = Date.now();
      if (DEBUG) { console.log('ping notification took', (this.tsNotificationReceived - this.tsCallDone), 'ms'); }

      if (this._ongoingPingCall) {
        this._ongoingPingCall.resolve(this.tsNotificationReceived - this.tsCallDone, topic);
      }

    },

    _run: function() {

      var errorID;
      var msg;

      // analyse last round
      if (this.tsCallDone === -1) {

        // check if ping call is still pending
        // call failed case is handled in _pingCallFailed() already
        if (this._ongoingPingCall && this._ongoingPingCall.state() === 'pending') {
          errorID = 'longrunning_ping_call';
          msg = 'Ping call runs for ' + (Date.now() - this.tsCallStarted) + ' ms already.';
        }

      } else if (this.tsNotificationReceived === -1) {
        errorID = 'missing_ping_notification';
        msg = 'No ping for ' + (Date.now() - this.tsCallDone) + ' ms already.';
      } else if (this.tsNotificationReceived - this.tsCallDone > 6000) {
        errorID = 'slow_ping';
        msg = 'Got ping after ' + (this.tsNotificationReceived - this.tsCallDone) + ' ms.';
      }

      if (errorID) {
        if (DEBUG) { console.error(msg); }
        var error = new NotificationError(errorID, msg);
        this.notificationCenter._informErrorListeners(error);
      }

      // next round
      this.sendPing();
    }
  };


  /**
   * @param errorID
   * @param msg
   * @param cause
   * @constructor
   */
  function NotificationError(errorID, msg, cause) {

    this.errorID = errorID;
    this.message = msg;
    this.cause   = cause;
    this.when    = Date.now();

  }

  window.haed = window.haed || {};
  window.haed.notification = {

    NotificationCenter: NotificationCenter,

    Channel: Channel,

    NotificationError: NotificationError,

    _call: call

  };
}());