package com.aiworkspace.audio.exceptions;

public class AudioSynthesisBusyException extends RuntimeException {

    public AudioSynthesisBusyException() {
        super("Speech synthesis is busy; retry later");
    }
}
