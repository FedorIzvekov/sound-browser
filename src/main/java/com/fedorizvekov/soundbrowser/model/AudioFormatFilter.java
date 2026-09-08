package com.fedorizvekov.soundbrowser.model;

public enum AudioFormatFilter {

    WAV_ONLY,
    WAV_AND_OGG,
    OGG_ONLY;


    public boolean includes(AudioFileType fileType) {
        return switch (this) {
            case WAV_ONLY -> fileType == AudioFileType.WAV;
            case WAV_AND_OGG -> fileType == AudioFileType.WAV || fileType == AudioFileType.OGG;
            case OGG_ONLY -> fileType == AudioFileType.OGG;
        };
    }

}