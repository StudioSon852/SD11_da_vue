package vn.shoegroup.sd11.auth;

public record JwtPrincipal(long id, String email, String role, int roleId, String fullName) { }
