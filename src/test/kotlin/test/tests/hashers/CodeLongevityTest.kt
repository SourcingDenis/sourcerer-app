// Copyright 2017 Sourcerer Inc. All Rights Reserved.
// Author: Alexander Surkov (alex@sourcerer.io)

package test.tests.hashers

import app.api.MockApi
import app.hashers.CodeLine
import app.hashers.CodeLongevity
import app.hashers.RevCommitLine
import app.model.*

import test.utils.TestRepo

import kotlin.test.assertEquals

import org.eclipse.jgit.revwalk.RevCommit

import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it

/**
 * Testing class.
 */
class CodeLongevityTest : Spek({

    /**
     * Assert function to test RevCommitLine object.
     */
    fun assertRevCommitLine(expectedCommit: RevCommit,
                            expectedFile: String,
                            expectedLineNum: Int,
                            actualLine: RevCommitLine,
                            messsage: String = "") {

        assertEquals(expectedCommit, actualLine.commit, "$messsage commit")
        assertEquals(expectedFile, actualLine.file, "$messsage file name")
        assertEquals(expectedLineNum, actualLine.line, "$messsage line num")
    }

    /**
     * Assert function to test CodeLine object.
     */
    fun assertCodeLine(lineText: String,
                       fromCommit: RevCommit, fromFile: String, fromLineNum: Int,
                       toCommit: RevCommit, toFile: String, toLineNum: Int,
                       actualLine: CodeLine) {

        assertRevCommitLine(fromCommit, fromFile, fromLineNum, actualLine.from,
                            "'$lineText' from_commit");
        assertRevCommitLine(toCommit, toFile, toLineNum, actualLine.to,
                            "'$lineText' to_commit");
        assertEquals(lineText, actualLine.text, "line text")
    }

    given("'test group #1'") {
        val testRepoPath = "../testrepo1"
        val testRepo = TestRepo(testRepoPath)
        val fileName = "test1.txt"

        // t1: initial insertion
        testRepo.createFile(fileName, listOf("line1", "line2"))
        val rev1 = testRepo.commit("inital commit")
        val lines1 = CodeLongevity(
            LocalRepo(testRepoPath), Repo(), MockApi(), testRepo.git).compute()

        it("'t1: initial insertion'") {
            assertEquals(2, lines1.size)
            assertCodeLine("line1",
                           rev1, fileName, 0,
                           rev1, fileName, 0,
                           lines1[0])
            assertCodeLine("line2",
                           rev1, fileName, 1,
                           rev1, fileName, 1,
                           lines1[1])
        }

        // t2: subsequent insertion
        testRepo.insertLines(fileName, 1, listOf("line in the middle"))
        val rev2 = testRepo.commit("insert line")
        val lines2 = CodeLongevity(
            LocalRepo(testRepoPath), Repo(), MockApi(), testRepo.git).compute()

        it("'t2: subsequent insertion'") {
            assertEquals(3, lines2.size)
            assertCodeLine("line in the middle",
                           rev2, fileName, 1,
                           rev2, fileName, 1,
                           lines2[0])
            assertCodeLine("line1",
                           rev1, fileName, 0,
                           rev2, fileName, 0,
                           lines2[1])
            assertCodeLine("line2",
                           rev1, fileName, 1,
                           rev2, fileName, 2,
                           lines2[2])
        }

        // t3: subsequent deletion
        testRepo.deleteLines(fileName, 2, 2)
        val rev3 = testRepo.commit("delete line")
        val lines3 = CodeLongevity(LocalRepo(testRepoPath), Repo(),
                                   MockApi(), testRepo.git).compute()

        it("'t3: subsequent deletion'") {
            assertEquals(3, lines3.size)
            assertCodeLine("line in the middle",
                           rev2, fileName, 1,
                           rev3, fileName, 1,
                           lines3[0])
            assertCodeLine("line1",
                           rev1, fileName, 0,
                           rev3, fileName, 0,
                           lines3[1])
            assertCodeLine("line2",
                           rev1, fileName, 1,
                           rev3, fileName, 2,
                           lines3[2])
        }

        // t4: file deletion
        testRepo.deleteFile(fileName)
        val rev4 = testRepo.commit("delete file")
        val lines4 = CodeLongevity(LocalRepo(testRepoPath), Repo(),
                                   MockApi(), testRepo.git).compute()

        it("'t4: file deletion'") {
            assertEquals(3, lines4.size)
            assertCodeLine("line in the middle",
                           rev2, fileName, 1,
                           rev4, fileName, 1,
                           lines4[0])
            assertCodeLine("line1",
                           rev1, fileName, 0,
                           rev4, fileName, 0,
                           lines4[1])

            assertCodeLine("line2",
                           rev1, fileName, 1,
                           rev3, fileName, 2,
                           lines4[2])
        }

        afterGroup {
            testRepo.destroy()
        }
    }

    given("'test group #2'") {

        val testRepoPath = "../testrepo2"
        val testRepo = TestRepo(testRepoPath)
        val fileName = "test1.txt"

        // t2.1: initial insertion
        val fileContent = listOf(
          "line1",
          "line2",
          "line3",
          "line4",
          "line4",
          "line5",
          "line6",
          "line7",
          "line8",
          "line9",
          "line10",
          "line11",
          "line12",
          "line13",
          "line14",
          "line15",
          "line16",
          "line17",
          "line18"
        )
        testRepo.createFile(fileName, fileContent)
        val rev1 = testRepo.commit("inital commit")
        val lines1 = CodeLongevity(
            LocalRepo(testRepoPath), Repo(), MockApi(), testRepo.git).compute()

        it("'t2.1: initial insertion'") {
            assertEquals(fileContent.size, lines1.size)
            for (idx in 0 .. fileContent.size - 1) {
                assertCodeLine(fileContent[idx],
                               rev1, fileName, idx,
                               rev1, fileName, idx,
                               lines1[idx])
            }
        }

        // t2.2: ins+del
        testRepo.deleteLines(fileName, 15, 18)
        testRepo.deleteLines(fileName, 9, 11)
        testRepo.deleteLines(fileName, 3, 5)
        testRepo.insertLines(fileName, 3, listOf("Proof addition 1"))
        testRepo.insertLines(fileName, 7, listOf("Proof addition 2"))
        testRepo.insertLines(fileName, 11, listOf("Proof addition 3"))
        val rev2 = testRepo.commit("insert+delete")

        val lines2 = CodeLongevity(
            LocalRepo(testRepoPath), Repo(), MockApi(), testRepo.git).compute()

        it("'t2.2: ins+del'") {
            assertEquals(22, lines2.size)
            assertCodeLine("Proof addition 3", rev2, fileName, 11,
                           rev2, fileName, 11, lines2[0])
            assertCodeLine("Proof addition 2", rev2, fileName, 7,
                           rev2, fileName, 7, lines2[1])
            assertCodeLine("Proof addition 1", rev2, fileName, 3,
                           rev2, fileName, 3, lines2[2])
            assertCodeLine("line1", rev1, fileName, 0,
                           rev2, fileName, 0, lines2[3])
            assertCodeLine("line2",
                           rev1, fileName, 1, rev2, fileName, 1, lines2[4])
            assertCodeLine("line3",
                           rev1, fileName, 2, rev2, fileName, 2, lines2[5])
            assertCodeLine("line4",
                           rev1, fileName, 3, rev2, fileName, 3, lines2[6])
            assertCodeLine("line4",
                           rev1, fileName, 4, rev2, fileName, 4, lines2[7])
            assertCodeLine("line5",
                           rev1, fileName, 5, rev2, fileName, 5, lines2[8])
        }

        afterGroup {
            testRepo.destroy()
        }
    }
})
