package com.wenwen.ai.scope;

/** Only the server authenticator may install this mapped identity in a session. */
public final class AuthenticatedDoctorSession implements java.io.Serializable {
    private static final long serialVersionUID=1L;
    public static final String ATTRIBUTE=AuthenticatedDoctorSession.class.getName();
    private final long doctorId;
    public AuthenticatedDoctorSession(TrustedDoctor doctor){if(doctor==null)throw new IllegalArgumentException("Missing mapped identity");doctorId=doctor.getId();}
    public TrustedDoctor getDoctor(){return new TrustedDoctor(doctorId);}
}
