package com.cloudera.frisch.keytabretriever;


import lombok.AllArgsConstructor;


@AllArgsConstructor
public class KeytabDescription {

  String principal;
  byte[] keytab;
  String keytabName;

}
