package com.dazednconfused.catalauncher.mod;

import com.dazednconfused.catalauncher.database.base.DisposableDatabase;
import com.dazednconfused.catalauncher.helper.Paths;
import com.dazednconfused.catalauncher.helper.result.Result;
import com.dazednconfused.catalauncher.mod.dto.ModDTO;
import com.dazednconfused.catalauncher.mod.dto.ModfileDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class ModManagerTest {

    private static ModManager instance;

    @BeforeAll
    public static void setup() {
        instance = ModManager.getInstance();
    }

    @AfterEach
    public void teardown() {
        ((DisposableDatabase) instance.modRepository).reset();
    }
    
    @AfterAll
    public static void cleanup() {
        ((DisposableDatabase) instance.modRepository).destroy();
    }

    @Test
    void register_mod_success() {

        // prepare mock data ---
        ModfileDTO MOCKED_MODFILE_1 = ModfileDTO.builder()
                .path("/a/mocked/1.path")
                .hash("aMockedHash1")
                .build();

        ModfileDTO MOCKED_MODFILE_2 = ModfileDTO.builder()
                .path("/a/mocked/2.path")
                .hash("aMockedHash2")
                .build();

        ModfileDTO MOCKED_MODFILE_3 = ModfileDTO.builder()
                .path("/a/mocked/3.path")
                .hash("aMockedHash3")
                .build();

        ModDTO MOCKED_MOD = ModDTO.builder()
                .name("mockedMod")
                .modinfo("mockedModInfo")
                .modfiles(Arrays.asList(
                        MOCKED_MODFILE_1,
                        MOCKED_MODFILE_2,
                        MOCKED_MODFILE_3
                ))
                .build();

        // execute test ---
        Result<Throwable, ModDTO> result = instance.registerMod(MOCKED_MOD);

        // verify assertions ---
        assertThat(result).isNotNull(); // assert non-null result

        assertThat(result.toEither().isRight()).isTrue(); // assert that Result is Success

        assertThat(result.toEither().get().getResult().isEmpty()).isFalse(); // assert that Result's Success is not empty


        ModDTO ACTUAL_RESULT = result.toEither().get().getResult().get();

        assertThat(ACTUAL_RESULT).isNotNull();
        assertThat(ACTUAL_RESULT.getName()).isEqualTo("mockedMod");
        assertThat(ACTUAL_RESULT.getModinfo()).isEqualTo("mockedModInfo");
        assertThat(ACTUAL_RESULT.getModfiles()).isNotNull();
        assertThat(ACTUAL_RESULT.getModfiles()).hasSize(3);


        ModfileDTO EXPECTED_MODFILE_1 = ModfileDTO.builder()
                .modId(ACTUAL_RESULT.getId())
                .path("/a/mocked/1.path")
                .hash("aMockedHash1")
                .build();

        ModfileDTO EXPECTED_MODFILE_2 = ModfileDTO.builder()
                .modId(ACTUAL_RESULT.getId())
                .path("/a/mocked/2.path")
                .hash("aMockedHash2")
                .build();

        ModfileDTO EXPECTED_MODFILE_3 = ModfileDTO.builder()
                .modId(ACTUAL_RESULT.getId())
                .path("/a/mocked/3.path")
                .hash("aMockedHash3")
                .build();

        assertThat(ACTUAL_RESULT.getModfiles()).usingRecursiveFieldByFieldElementComparatorIgnoringFields(
                "id", "createdDate", "updatedDate"
        ).containsExactlyInAnyOrder(
                EXPECTED_MODFILE_1,
                EXPECTED_MODFILE_2,
                EXPECTED_MODFILE_3
        );

        assertThat(ACTUAL_RESULT.getModfiles()).extracting(ModfileDTO::getCreatedDate).allSatisfy(createdDate -> assertThat(createdDate).isNotNull());
        assertThat(ACTUAL_RESULT.getModfiles()).extracting(ModfileDTO::getUpdatedDate).allSatisfy(updatedDate -> assertThat(updatedDate).isNotNull());
        assertThat(ACTUAL_RESULT.getModfiles()).allSatisfy(dto -> assertThat(dto.getCreatedDate()).isEqualTo(dto.getUpdatedDate()));
    }

    @Test
    void list_all_registered_mods_success() {

        // prepare mock data ---
        ModfileDTO MOCKED_MODFILE_1_1 = ModfileDTO.builder()
                .path("/a/mocked/1/1.path")
                .hash("aMockedHash1_1")
                .build();

        ModfileDTO MOCKED_MODFILE_1_2 = ModfileDTO.builder()
                .path("/a/mocked/1/2.path")
                .hash("aMockedHash1_2")
                .build();

        ModfileDTO MOCKED_MODFILE_1_3 = ModfileDTO.builder()
                .path("/a/mocked/1/3.path")
                .hash("aMockedHash1_3")
                .build();

        ModDTO MOCKED_MOD_1 = ModDTO.builder()
                .name("mockedMod1")
                .modinfo("mockedModInfo1")
                .modfiles(Arrays.asList(
                        MOCKED_MODFILE_1_1,
                        MOCKED_MODFILE_1_2,
                        MOCKED_MODFILE_1_3
                ))
                .build();


        ModfileDTO MOCKED_MODFILE_2_1 = ModfileDTO.builder()
                .path("/a/mocked/2/1.path")
                .hash("aMockedHash2_1")
                .build();

        ModfileDTO MOCKED_MODFILE_2_2 = ModfileDTO.builder()
                .path("/a/mocked/2/2.path")
                .hash("aMockedHash2_2")
                .build();

        ModfileDTO MOCKED_MODFILE_2_3 = ModfileDTO.builder()
                .path("/a/mocked/2/3.path")
                .hash("aMockedHash2_3")
                .build();

        ModDTO MOCKED_MOD_2 = ModDTO.builder()
                .name("mockedMod2")
                .modinfo("mockedModInfo2")
                .modfiles(Arrays.asList(
                        MOCKED_MODFILE_2_1,
                        MOCKED_MODFILE_2_2,
                        MOCKED_MODFILE_2_3
                ))
                .build();


        ModfileDTO MOCKED_MODFILE_3_1 = ModfileDTO.builder()
                .path("/a/mocked/3/1.path")
                .hash("aMockedHash3_1")
                .build();

        ModfileDTO MOCKED_MODFILE_3_2 = ModfileDTO.builder()
                .path("/a/mocked/3/2.path")
                .hash("aMockedHash3_2")
                .build();

        ModfileDTO MOCKED_MODFILE_3_3 = ModfileDTO.builder()
                .path("/a/mocked/3/3.path")
                .hash("aMockedHash3_3")
                .build();

        ModDTO MOCKED_MOD_3 = ModDTO.builder()
                .name("mockedMod3")
                .modinfo("mockedModInfo3")
                .modfiles(Arrays.asList(
                        MOCKED_MODFILE_3_1,
                        MOCKED_MODFILE_3_2,
                        MOCKED_MODFILE_3_3
                ))
                .build();

        ModDTO EXPECTED_RESULT_1 = instance.registerMod(MOCKED_MOD_1).toEither().get().getResult().orElseThrow();
        ModDTO EXPECTED_RESULT_2 = instance.registerMod(MOCKED_MOD_2).toEither().get().getResult().orElseThrow();
        ModDTO EXPECTED_RESULT_3 = instance.registerMod(MOCKED_MOD_3).toEither().get().getResult().orElseThrow();

        // execute test ---
        List<ModDTO> result = instance.listAllRegisteredMods();

        // verify assertions ---
        assertThat(result).containsExactlyInAnyOrder(
                EXPECTED_RESULT_1,
                EXPECTED_RESULT_2,
                EXPECTED_RESULT_3
        );
    }

    @Test
    void unregister_mod_success() {

        // prepare mock data ---
        ModfileDTO MOCKED_MODFILE_1_1 = ModfileDTO.builder()
                .path("/a/mocked/1/1.path")
                .hash("aMockedHash1_1")
                .build();

        ModfileDTO MOCKED_MODFILE_1_2 = ModfileDTO.builder()
                .path("/a/mocked/1/2.path")
                .hash("aMockedHash1_2")
                .build();

        ModfileDTO MOCKED_MODFILE_1_3 = ModfileDTO.builder()
                .path("/a/mocked/1/3.path")
                .hash("aMockedHash1_3")
                .build();

        ModDTO MOCKED_MOD_1 = ModDTO.builder()
                .name("mockedMod1")
                .modinfo("mockedModInfo1")
                .modfiles(Arrays.asList(
                        MOCKED_MODFILE_1_1,
                        MOCKED_MODFILE_1_2,
                        MOCKED_MODFILE_1_3
                ))
                .build();


        ModfileDTO MOCKED_MODFILE_2_1 = ModfileDTO.builder()
                .path("/a/mocked/2/1.path")
                .hash("aMockedHash2_1")
                .build();

        ModfileDTO MOCKED_MODFILE_2_2 = ModfileDTO.builder()
                .path("/a/mocked/2/2.path")
                .hash("aMockedHash2_2")
                .build();

        ModfileDTO MOCKED_MODFILE_2_3 = ModfileDTO.builder()
                .path("/a/mocked/2/3.path")
                .hash("aMockedHash2_3")
                .build();

        ModDTO MOCKED_MOD_2 = ModDTO.builder()
                .name("mockedMod2")
                .modinfo("mockedModInfo2")
                .modfiles(Arrays.asList(
                        MOCKED_MODFILE_2_1,
                        MOCKED_MODFILE_2_2,
                        MOCKED_MODFILE_2_3
                ))
                .build();


        ModfileDTO MOCKED_MODFILE_3_1 = ModfileDTO.builder()
                .path("/a/mocked/3/1.path")
                .hash("aMockedHash3_1")
                .build();

        ModfileDTO MOCKED_MODFILE_3_2 = ModfileDTO.builder()
                .path("/a/mocked/3/2.path")
                .hash("aMockedHash3_2")
                .build();

        ModfileDTO MOCKED_MODFILE_3_3 = ModfileDTO.builder()
                .path("/a/mocked/3/3.path")
                .hash("aMockedHash3_3")
                .build();

        ModDTO MOCKED_MOD_3 = ModDTO.builder()
                .name("mockedMod3")
                .modinfo("mockedModInfo3")
                .modfiles(Arrays.asList(
                        MOCKED_MODFILE_3_1,
                        MOCKED_MODFILE_3_2,
                        MOCKED_MODFILE_3_3
                ))
                .build();

        ModDTO EXPECTED_TO_BE_UNREGISTERED = instance.registerMod(MOCKED_MOD_1).toEither().get().getResult().orElseThrow();
        ModDTO EXPECTED_TO_REMAIN_1 = instance.registerMod(MOCKED_MOD_2).toEither().get().getResult().orElseThrow();
        ModDTO EXPECTED_TO_REMAIN_2 = instance.registerMod(MOCKED_MOD_3).toEither().get().getResult().orElseThrow();

        // pre-test assertions ---
        assertThat(instance.listAllRegisteredMods()).containsExactlyInAnyOrder(
                EXPECTED_TO_BE_UNREGISTERED,
                EXPECTED_TO_REMAIN_1,
                EXPECTED_TO_REMAIN_2
        );

        // execute test ---
        Result<Throwable, Void> result = instance.unregisterMod(EXPECTED_TO_BE_UNREGISTERED);

        // verify assertions ---
        assertThat(result).isNotNull(); // assert non-null result

        assertThat(result.toEither().isRight()).isTrue(); // assert that Result is Success

        assertThat(result.toEither().get().getResult().isEmpty()).isTrue(); // assert that Result's Success is empty

        assertThat(instance.listAllRegisteredMods()).containsExactlyInAnyOrder(
                EXPECTED_TO_REMAIN_1,
                EXPECTED_TO_REMAIN_2
        );
    }

    @Test
    void get_mods_folder_success(@TempDir Path mockedDirectory) {
        try (MockedStatic<Paths> mockedPaths = mockStatic(Paths.class)) {

            // prepare mock data ---
            mockedPaths.when(Paths::getCustomModsDir).thenReturn(mockedDirectory.toString());

            // execute test ---
            File result = instance.getModsFolder();

            // verify assertions ---
            assertThat(result).isEqualTo(new File(mockedDirectory.toString()));
        }
    }

    @Test
    void validate_mod_for_zip_success() {

        // prepare mock data ---
        File MOCKED_MOD_ZIP = getFromResource("mod/sample/zipped/cdda_mutation_rebalance_mod.zip");

        // execute test ---
        Result<Throwable, File> result = instance.validateMod(MOCKED_MOD_ZIP);

        // verify assertions ---
        assertThat(result).isNotNull(); // assert non-null result

        assertThat(result.toEither().isRight()).isTrue(); // assert that Result is Success

        assertThat(result.toEither().get().getResult().isEmpty()).isFalse(); // assert that Result's Success is not empty


        File ACTUAL_RESULT = result.toEither().get().getResult().orElseThrow();

        List<String> EXPECTED_RELATIVE_PATHS = Arrays.asList(
                "modinfo.json",
                "README.md",
                "items/armor/integrated.json"
        );

        // collect all file paths recursively
        List<File> ACTUAL_FILES = new ArrayList<>();
        collectAllFilesFromInto(ACTUAL_RESULT, ACTUAL_FILES);

        // convert actual files to relative paths
        List<String> ACTUAL_RELATIVE_PATHS = ACTUAL_FILES.stream()
                .map(file -> ACTUAL_RESULT.toPath().relativize(file.toPath()).toString())
                .collect(Collectors.toList());

        // assert unzipped mod contents
        assertThat(ACTUAL_RELATIVE_PATHS).containsAll(EXPECTED_RELATIVE_PATHS);
    }

    @Test
    void validate_mod_for_directory_success() {

        // prepare mock data ---
        File MOCKED_MOD_ZIP = getFromResource("mod/sample/unzipped/cdda_mutation_rebalance_mod");

        // execute test ---
        Result<Throwable, File> result = instance.validateMod(MOCKED_MOD_ZIP);

        // verify assertions ---
        assertThat(result).isNotNull(); // assert non-null result

        assertThat(result.toEither().isRight()).isTrue(); // assert that Result is Success

        assertThat(result.toEither().get().getResult().isEmpty()).isFalse(); // assert that Result's Success is not empty


        File ACTUAL_RESULT = result.toEither().get().getResult().orElseThrow();

        List<String> EXPECTED_RELATIVE_PATHS = Arrays.asList(
                "modinfo.json",
                "README.md",
                "items/armor/integrated.json"
        );

        // collect all file paths recursively
        List<File> ACTUAL_FILES = new ArrayList<>();
        collectAllFilesFromInto(ACTUAL_RESULT, ACTUAL_FILES);

        // convert actual files to relative paths
        List<String> ACTUAL_RELATIVE_PATHS = ACTUAL_FILES.stream()
                .map(file -> ACTUAL_RESULT.toPath().relativize(file.toPath()).toString())
                .collect(Collectors.toList());

        // assert unzipped mod contents
        assertThat(ACTUAL_RELATIVE_PATHS).containsAll(EXPECTED_RELATIVE_PATHS);
    }

    /**
     * Collects all {@link File}s from {@code sourceDirectory} into the given {@code allFiles} array.
     * */
    private void collectAllFilesFromInto(File sourceDirectory, List<File> allFiles) {
        File[] files = sourceDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    collectAllFilesFromInto(file, allFiles);
                } else {
                    allFiles.add(file);
                }
            }
        }
    }

    /**
     * Retrieves the given {@code fileName} from the Java Resources' folder.
     * */
    private static File getFromResource(String fileName) {
        URL resourceUrl = ModManagerTest.class.getClassLoader().getResource(fileName);

        // check that the resource exists
        if (resourceUrl != null) {
            return new File(resourceUrl.getFile()); // convert the URL to a File object
        }

        throw new IllegalArgumentException("Could not find resource: " + fileName);
    }
}