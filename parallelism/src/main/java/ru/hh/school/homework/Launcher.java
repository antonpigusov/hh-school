package ru.hh.school.homework;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import static java.util.Collections.reverseOrder;
import static java.util.Map.Entry.comparingByValue;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;
import static java.util.stream.Collectors.toSet;

// Написать код, который, как можно более параллельно:
// - по заданному пути найдет все "*.java" файлы
// - для каждого файла вычислит 10 самых популярных слов (см. #naiveCount())
// - соберет top 10 для каждой папки в которой есть хотя-бы один java файл
// - для каждого слова сходит в гугл и вернет количество результатов по нему (см. #naiveSearch())
// - распечатает в консоль результаты в виде:
// <папка1> - <слово #1> - <кол-во результатов в гугле>
// <папка1> - <слово #2> - <кол-во результатов в гугле>
// ...
// <папка1> - <слово #10> - <кол-во результатов в гугле>
// <папка2> - <слово #1> - <кол-во результатов в гугле>
// <папка2> - <слово #2> - <кол-во результатов в гугле>
// ...
// <папка2> - <слово #10> - <кол-во результатов в гугле>
// ...
//
// Порядок результатов в консоли не обязательный.
// При желании naiveSearch и naiveCount можно оптимизировать.

public class Launcher {
  public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
    Path path = Path.of("D:\\HH_Lessons\\7. Concurrency\\hh-school\\parallelism\\src\\main\\java\\ru\\hh\\school\\parallelism");
    long time1 = System.nanoTime();
    List<Path> dirs = new ArrayList<>(getSubPaths(path));
    dirs.add(path);
    dirs.forEach(System.out::println);
    System.out.println();
    printAllFiles(dirs); // для отладки
    Map<Path, List<Path>> dirToFilesMap= dirToFilesMapping(dirs);
    dirToFilesMap.entrySet().forEach(System.out::println);
    System.out.println();
    Map<Path, Set<String>> dirToWords = dirToWordsMapping(dirToFilesMap);
    dirToWords.entrySet().forEach(System.out::println);
    System.out.println();
    printAnswers(dirToWords);
    System.out.printf("Time spent in ms: %s\n", (System.nanoTime() - time1)/1000000);
  }

  private static void printWords(Answer answer){
    System.out.println(answer.getFolder() + "-----" + answer.getWord() + "-----" + naiveSearch(answer.getWord()));
  }
  private static Set<Answer> getWordsMapping (Path path, Set<String> words){
    return words.parallelStream()
            .map(s -> new Answer(path.toString(), s))
            .collect(toSet());
  }

  private static List<Path> getSubPaths(Path path) throws IOException {
    return Files.list(path)
            .parallel()
            .filter(p -> Files.isDirectory(p))
            .toList();
  }

  private static void printAllFiles(List<Path> dirs){
    dirs.parallelStream()
            .map(p -> naiveFiles(p))
            .flatMap(Collection::stream)
            .collect(Collectors.toList()).forEach(System.out::println);
    System.out.println();
  }

  private static Map<Path, List<Path>> dirToFilesMapping(List<Path> dirs){
    return dirs.parallelStream()
            .collect(toMap(identity(), p -> naiveFiles(p)));
  }

  private static Map<Path, Set<String>> dirToWordsMapping(Map<Path, List<Path>> dirToFilesMap){
    return dirToFilesMap.entrySet()
            .parallelStream()
            .collect(toMap(Map.Entry::getKey, d -> top10Words(d.getValue())));
  }

  private static void printAnswers(Map<Path, Set<String>> dirToWords){
    CompletableFuture<Void> future = new CompletableFuture<>();
    ExecutorService executor = Executors.newFixedThreadPool(20);
    List<CompletableFuture<Void>> futures = dirToWords.entrySet()
            .parallelStream()
            .map(d -> getWordsMapping(d.getKey(), d.getValue()))
            .flatMap(Collection::parallelStream)
            .map(s -> future.runAsync(() -> printWords(s), executor)).toList();
    futures.forEach(c -> {
      try {
        c.join();
      } catch (RuntimeException ex){
        System.out.println(ex.getMessage());
        c.complete(null);
      }
    });
    executor.shutdownNow();
  }

  private static List<Path> naiveFiles(Path path) {
    try {
      return Files.list(path)
              .parallel()
              .filter(p -> p.getFileName().toString().endsWith(".java"))
              .collect(Collectors.toList());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static Set<String> top10Words (List<Path> files){
    return files.parallelStream()
            .map(p -> naiveCount(p))
            .flatMap(m -> m.entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey,
                    Map.Entry::getValue,
                    (value1, value2) -> Math.max(value1, value2)))
            .entrySet()
            .parallelStream()
            .sorted(comparingByValue(reverseOrder()))
            .limit(10)
            .map(Map.Entry::getKey)
            .collect(toSet());
  }

  private static Map<String, Long> naiveCount(Path path) {
    try {
      return Files.lines(path)
              .parallel()
              .flatMap(line -> Stream.of(line.split("[^a-zA-Z0-9]")))
              .filter(word -> word.length() > 3)
              .collect(groupingBy(identity(), counting()))
              .entrySet()
              .parallelStream()
              .sorted(comparingByValue(reverseOrder()))
              .limit(10)
              .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static long naiveSearch(String query) {
    Document document = null;
    try {
      document = Jsoup //
              .connect("https://www.google.com/search?q=" + query) //
              .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.116 Safari/537.36") //
              .get();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    Element divResultStats = document.select("div#result-stats").first();
    String text = divResultStats.text();
    try {
      String resultsPart = text.substring(0, text.indexOf('('));
      return Long.parseLong(resultsPart.replaceAll("[^0-9]", ""));
    } catch (StringIndexOutOfBoundsException ex){
      System.out.println(query);
    }
    return 0;
  }

}
