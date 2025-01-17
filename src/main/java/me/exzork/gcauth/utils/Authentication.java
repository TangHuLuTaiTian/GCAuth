package me.exzork.gcauth.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.Account;
import me.exzork.gcauth.GCAuth;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;

import java.sql.Date;
import java.time.Instant;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public final class Authentication {
    public static final HashMap<String, String> otps = new HashMap<>();
    private static Algorithm key = Algorithm.HMAC256(generateRandomNumber(32));
    public static Algorithm getKey() {
        return key;
    }

    public static Account getAccountByUsernameAndPassword(String username, String password) {
        Account account = DatabaseHelper.getAccountByName(username);
        if (account == null) return null;
        if (account.getPassword() != null && !account.getPassword().isEmpty()) {
            if (!verifyPassword(password, account.getPassword())) account = null;
        }
        return account;
    }

    public static Account getAccountByOTP(String otp) {
        String username = Authentication.otps.get(otp);
        if (username == null) return null;
        Authentication.otps.remove(otp);
        return DatabaseHelper.getAccountByName(username);
    }

    public static String generateRandomNumber(int length) {
        String chars = "0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }

    public static String generateRandomString(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }

    public static String generateOTP(Account account) {
        String otp = Authentication.generateRandomNumber(6);
        while (otps.containsKey(otp)) {
            GCAuth.getInstance().getLogger().info("OTP already in use, generating new one");
            otp = Authentication.generateRandomNumber(6);
        }
        Authentication.otps.put(otp, account.getUsername());
        return otp;
    }

    public static String generateJwt(Account account) {
        String otp = generateOTP(account);
        watchOTP(otp);
        return JWT.create()
                .withClaim("token", otp)
                .withClaim("username", account.getUsername())
                .withClaim("uid", account.getPlayerUid())
                .withExpiresAt(Date.from(Instant.ofEpochSecond(System.currentTimeMillis() / 1000 + GCAuth.getInstance().getConfig().jwtExpiration)))
                .sign(key);
    }

    public static String generateHash(String password) {
        return switch (GCAuth.getInstance().getConfig().Hash.toLowerCase()) {
            case "bcrypt" -> new BCryptPasswordEncoder().encode(password);
            case "scrypt" -> new SCryptPasswordEncoder().encode(password);
            default -> password;
        };
    }

    private static boolean verifyPassword(String password, String hash) {
        return switch (GCAuth.getInstance().getConfig().Hash.toLowerCase()) {
            case "bcrypt" -> new BCryptPasswordEncoder().matches(password, hash);
            case "scrypt" -> new SCryptPasswordEncoder().matches(password, hash);
            default -> password.equals(hash);
        };
    }

    public static String getUsernameFromJwt(String jwt) {
        String username = null;
        try {
            username = JWT.require(key)
                    .build()
                    .verify(jwt)
                    .getClaim("username")
                    .asString();
        }catch (Exception ignored) {}
        return username;
    }

    public static void watchOTP(String otp){
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                otps.remove(otp);
                timer.cancel();
            }
        },GCAuth.getInstance().getConfig().otpExpiration * 1000);
    }
}
