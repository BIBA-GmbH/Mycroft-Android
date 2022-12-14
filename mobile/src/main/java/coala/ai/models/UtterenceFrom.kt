package coala.ai.models

enum class UtteranceFrom(val id: Int) {
    USER(0),
    MYCROFT(1),
    USER_IMG(2),
    BUTTONS(3),
    MYCROFT_IMG(4),
    TABLE(5),
    //Added one extra if for buttons in chat history
    //Needed so that buttons in chat history look like normal buttons
    //but are non-clickable
    BUTTONS_CHAT(6)

}