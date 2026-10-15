package com.jobportal.service;

/** A resume loaded from disk, ready to be sent to the browser. */
public record ResumeFile(String originalName, byte[] data) {}
