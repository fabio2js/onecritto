package com.onecritto.persistence;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileMeta {

    private String id;
    private String name;
    private String contentType;

    private long offset;   // posizione nel file .onecritto dove inizia il blob cifrato
    private long size;     // lunghezza del contenuto cifrato
    private long lastEdit;
    private long addTime;

}