
var app = {};
app.ChannelWidget = function() {
  
  return function() {
    
    var notificationCenter, channelDiv;
    
    channelDiv = jQuery("<div class='channel-widget' />")
      .append(jQuery("<div class='channel-id' />"))
      .append(
          '<input class="topic" type="text" /><button class="subscribe" style="width: 80px">Subscribe</button>' + 
          '<br/><input class="message" type="text" style="width: 400px" /><button class="send" style="width: 80px">Send</button>' + 
          
          '<br/><button class="clear" style="width: 80px">Clear</button>' + 
          '<button class="stress10" style="width: 80px">send 10 Messages</button>' + 
          '<button class="stress100" style="width: 80px">send 100 Messages</button>' + 
          '<button class="ping" style="width: 80px">ping</button>' + 
          '<span class="count" style="width: 80px">Count: 0</span>' + 
          '<button class="resetCount" style="width: 80px">resetCount</button>' + 
          '<br/><div class="console"></div>')
      .appendTo(".page");
    
    
    /* clears output */
    channelDiv.find(".clear").click(function() {
        channelDiv.find(".console").html("");
      });

    var println = function(text) {
      channelDiv.find(".console")
        .prepend(jQuery("<div>" + text + "</div>"));
    };

    var sendMessage = function(topic, message) {
      return jQuery.ajax("/haed.app1/send", {
            type: "GET", 
            data: { topic: topic, message: message }
          })
        .done(function() { println("send to " + topic + ": " + message); });
    };

    var sendMessages = function(max) {
      var topic = channelDiv.find(".topic").val();
      for (var i = 1; i <= max; i++) {
        sendMessage(topic, "stress1, message " + i + "/" + max);
      }
    };

    var count = 0;
    var incCount = function(i) {
      count+=i;
      channelDiv.find(".count").text("Count: " + count);
    };
    var decCount = function(i) {
      count-=i;
      channelDiv.find(".count").text("Count: " + count);
    };
    var resetCount = function() {
      count=0;
      channelDiv.find(".count").text("Count: " + count);
    };

    channelDiv.find(".stress10").click(function() { sendMessages(10); });
    channelDiv.find(".stress100").click(function() { sendMessages(100); });

    channelDiv.find(".resetCount").click(resetCount);

    channelDiv.find(".ping").click(function() {
        notificationCenter.getPingHandler().sendPing()
          .done(function(millis) {
            alert("ping was successful, after " + millis + "ms");
          })
          .fail(function(error) {
            alert("ping failed: " + (error && (error.errorID + ' - ' + error.message)));
          });
      });
    
    // initializes the channel new channel (got new channelID from server)
    notificationCenter = new haed.notification.NotificationCenter();
    notificationCenter.getOrCreateChannel()
      .done(function(channel) {
          
          channelDiv.find(".channel-id").html(channel.channelID);
          
          channelDiv.find(".subscribe").click(function() {
              var topic = channelDiv.find(".topic").val();
              jQuery.ajax("/haed.app1/subscribe", {
                    type: "GET", 
                    data: {
                      channelID: channel.channelID,
                      topic: topic
                    }
                  })
                .done(function() {
                    notificationCenter.onMessage(function(message) {
                      println("received: " + message);
                      incCount(1);
                    }, topic);
                    println("subscribed to: " + topic);
                  });
            });
          
          channelDiv.find(".send").click(function() {
              sendMessage(channelDiv.find(".topic").val(), channelDiv.find(".message").val())
                .done(function() { channelDiv.find(".message").val(""); });
            });
        });
    
    
    return {
    };
  };
}();