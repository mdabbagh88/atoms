final PushSender sender =
    DefaultPushSender.withRootServerURL("{{ contextPath }}")
        .pushApplicationId("{{ app.pushApplicationID }}")
        .masterSecret("{{ app.masterSecret }}")
    .build();

final UnifiedMessage unifiedMessage = UnifiedMessage.
    withMessage()
        .alert("Hello from Java Sender API!")
    .build();


sender.send(unifiedMessage, new MessageResponseCallback() {

    @Override
    public void onComplete(int statusCode) {
        //do cool stuff
    }

    @Override
    public void onError(Throwable throwable) {
      //bring out the bad news
    }
});
