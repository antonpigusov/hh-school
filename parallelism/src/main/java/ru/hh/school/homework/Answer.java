package ru.hh.school.homework;

public class Answer {
    private String folder;
    private String word;

    public Answer(String folder, String word) {
        this.folder = folder;
        this.word = word;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    @Override
    public String toString() {
        return "Answer{" +
                "folder='" + folder + '\'' +
                ", word='" + word + '\'' +
                '}';
    }
}
