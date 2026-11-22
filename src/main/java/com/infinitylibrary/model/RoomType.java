package com.infinitylibrary.model;

public enum RoomType {
    STARTING, FILLER, BOOK, READ, RENTABLE, PUBLIC_WRITING;

    public static RoomType parse(String value) {
        if(value.equalsIgnoreCase("READING"))return READ;
        if(value.equalsIgnoreCase("RENT"))return RENTABLE;
        if(value.equalsIgnoreCase("WRITING"))return PUBLIC_WRITING;
        for (RoomType type : values()) {
            if (type.name().equalsIgnoreCase(value)) return type;
        }
        throw new IllegalArgumentException("Unknown room type: " + value);
    }
}
