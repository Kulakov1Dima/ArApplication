package com.example.iarsecuritysystem;

import com.google.ar.core.Anchor;
import com.google.ar.core.Trackable;

public class WrappedAnchor {
    private Anchor anchor;
    private Trackable trackable;
    private int isNew;

    public WrappedAnchor(Anchor anchor, Trackable trackable, int b) {
        this.anchor = anchor;
        this.trackable = trackable;
        this.isNew = b;
    }

    public Anchor getAnchor() {
        return anchor;
    }

    public Trackable getTrackable() {
        return trackable;
    }

    public int isNew() {
        return isNew;
    }
}