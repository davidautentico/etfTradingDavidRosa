package com.alphapowertrading.walkforward.model;
import java.time.LocalDate;
public record WalkForwardResult(int window,LocalDate isStart,LocalDate isEnd,LocalDate oosStart,LocalDate oosEnd,double tp,double tph,double sl,double isCagr,double isSharpe,double isMaxDrawdown,double oosCagr,double oosSharpe,double oosMaxDrawdown,double oosFinalEquity,int oosTrades,double oosWinRate,double oosToIsCagr) {}
