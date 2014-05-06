class simple {
    public void testFunction() {
        String xxx = <error descr="Zero width space character found, resulting in: \"mysql://u200Blocalhost/\"">"mysql://​localhost/"</error>;

    }
}
