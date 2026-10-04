package com.wishapp.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WishTest {

    @Test
    void toString_includesIdentityFieldsAndPictureLength() {
        Wish wish = wish(7L, "Cookbook");
        wish.setTags(new String[] {"cooking"});
        wish.setComment("yummy");
        wish.setPicture(new byte[] {1, 2, 3});

        assertThat(wish.toString())
            .contains("id=7")
            .contains("name='Cookbook'")
            .contains("tags=[cooking]")
            .contains("comment='yummy'")
            .contains("pictureLength=3");
    }

    @Test
    void toString_nullPicture_reportsZeroLength() {
        Wish wish = wish(1L, "Empty");
        wish.setPicture(null);

        assertThat(wish.toString()).contains("pictureLength=0");
    }

    private static Wish wish(Long id, String name) {
        Wish wish = new Wish();
        wish.setId(id);
        wish.setName(name);
        wish.setComment("comment");
        wish.setTags(new String[0]);
        wish.setPicture(new byte[0]);
        return wish;
    }
}
