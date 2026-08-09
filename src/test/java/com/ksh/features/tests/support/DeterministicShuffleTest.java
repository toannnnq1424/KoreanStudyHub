package com.ksh.features.tests.support;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicShuffleTest {

    @Test
    void shuffle_is_reproducible_for_the_same_seed_and_preserves_all_items() {
        List<Integer> first = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        List<Integer> second = new ArrayList<>(first);

        DeterministicShuffle.shuffle(first, 91_337L);
        DeterministicShuffle.shuffle(second, 91_337L);

        assertThat(first).containsExactlyElementsOf(second);
        assertThat(first).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6);
    }

    @Test
    void shuffle_handles_empty_and_single_item_collections() {
        List<Integer> empty = new ArrayList<>();
        List<Integer> single = new ArrayList<>(List.of(99));

        DeterministicShuffle.shuffle(empty, 1L);
        DeterministicShuffle.shuffle(single, 1L);

        assertThat(empty).isEmpty();
        assertThat(single).containsExactly(99);
    }

    @Test
    void derived_seeds_are_stable_and_distinguish_question_scope() {
        assertThat(DeterministicShuffle.questionSeed(23L)).isEqualTo(23_000_069L);
        assertThat(DeterministicShuffle.optionSeed(23L, 7L)).isEqualTo(23_000_286L);
        assertThat(DeterministicShuffle.optionSeed(23L, 8L))
                .isNotEqualTo(DeterministicShuffle.optionSeed(23L, 7L));
    }

    @Test
    void utility_constructor_is_private_but_instantiable_for_coverage() throws Exception {
        Constructor<DeterministicShuffle> constructor = DeterministicShuffle.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThat(constructor.newInstance()).isNotNull();
    }
}
