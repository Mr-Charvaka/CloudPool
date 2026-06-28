public class Hash {
    public static void main(String[] a) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        System.out.println(java.util.HexFormat.of().formatHex(md.digest("Drive content".getBytes())));
    }
}
