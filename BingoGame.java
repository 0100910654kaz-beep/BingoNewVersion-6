package servlet;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class BingoGame implements Serializable {
    private static final long serialVersionUID = 1L;

    private String gameId;                             
    private List<Integer> drawnNumbers;                
    private List<PlayerResult> bingoPlayers;           
    private List<PlayerResult> reachPlayers;           
    private List<String> allPlayers;                   
    private Date expireTime;                           
    private Date lastBingoTime;                        
    private int anonymousCount = 0;                    

    private ConcurrentHashMap<String, List<List<String>>> playerCards = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, List<String>> playerWaitNumbers = new ConcurrentHashMap<>();

    public BingoGame(String gameId, int validDays) {
        this.gameId = gameId;
        this.drawnNumbers = new CopyOnWriteArrayList<>();
        this.bingoPlayers = new CopyOnWriteArrayList<>();
        this.reachPlayers = new CopyOnWriteArrayList<>();
        this.allPlayers = new CopyOnWriteArrayList<>();
        this.lastBingoTime = new Date(); 

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, validDays);
        this.expireTime = cal.getTime();
    }

    public synchronized int getAnonymousCount() {
        return anonymousCount;
    }

    // ⚡【新機能】重複なしで「プレイヤー1」「プレイヤー2」を全自動で安全に割り振る
    public synchronized String generateNextPlayerName() {
        this.anonymousCount++;
        return "プレイヤー" + this.anonymousCount;
    }

    public List<List<String>> getPlayerCard(String name) {
        return playerCards.get(name);
    }

    public void setPlayerCard(String name, List<List<String>> card) {
        playerCards.put(name, card);
        if (!allPlayers.contains(name)) {
            allPlayers.add(name);
        }
    }

    public synchronized void registerBingo(String name, int lastNum) {
        boolean alreadyBingo = false;
        for (PlayerResult p : bingoPlayers) {
            if (p.getPlayerName().equals(name)) {
                alreadyBingo = true;
                break;
            }
        }
        if (!alreadyBingo) {
            bingoPlayers.add(0, new PlayerResult(name, new Date(), lastNum));
            lastBingoTime = new Date();
        }
    }

    public synchronized void removeBingo(String name) {
        bingoPlayers.removeIf(p -> p.getPlayerName().equals(name));
    }

    public synchronized void updateReachStatus(String name, boolean isReach, List<String> waitNums) {
        if (isReach) {
            playerWaitNumbers.put(name, waitNums);
            boolean alreadyReach = false;
            for (PlayerResult p : reachPlayers) {
                if (p.getPlayerName().equals(name)) {
                    alreadyReach = true;
                    break;
                }
            }
            if (!alreadyReach) {
                int lastNum = drawnNumbers.isEmpty() ? 0 : drawnNumbers.get(drawnNumbers.size() - 1);
                reachPlayers.add(new PlayerResult(name, new Date(), lastNum));
            }
        } else {
            reachPlayers.removeIf(p -> p.getPlayerName().equals(name));
            playerWaitNumbers.remove(name);
        }
    }

    public synchronized void resetGame() {
        drawnNumbers.clear();
        bingoPlayers.clear();
        reachPlayers.clear();
        playerWaitNumbers.clear();
        playerCards.clear();
        allPlayers.clear();
        anonymousCount = 0;
        lastBingoTime = new Date();
    }

    public synchronized List<String> getWaitNumbers(String name) {
        return playerWaitNumbers.getOrDefault(name, new ArrayList<>());
    }

    public boolean isExpired() { return new Date().after(this.expireTime); }
    
    public boolean isPast2HoursFromLastBingo() {
        if (drawnNumbers.isEmpty() && bingoPlayers.isEmpty()) return false;
        long twoHoursInMilliseconds = 2L * 60 * 60 * 1000;
        long timePassed = new Date().getTime() - lastBingoTime.getTime();
        return timePassed > twoHoursInMilliseconds;
    }

    public String getGameId() { return gameId; }\n    public List<Integer> getDrawnNumbers() { return drawnNumbers; }\n    public List<PlayerResult> getBingoPlayers() { return bingoPlayers; }\n    public List<PlayerResult> getReachPlayers() { return reachPlayers; }\n    public int getPlayerCount() { return playerCards.size(); }\n    public List<String> getAllPlayers() { return allPlayers; }\n}\n```

---

### 🛠️ 2. `BingoServlet.java`（全書き換え用・完全版）
プレイヤー画面から送られてくる名前入力パラメータを完全に撤廃し、参加（join）時にサーバー側で名前を自動確定させ、セッションと共通メモリ空間へカチッと同期保存するロジックに書き換えています。

```java
package servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/BingoServlet")
public class BingoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final ConcurrentHashMap<String, BingoGame> games = new ConcurrentHashMap<>();

    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        processRequest(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        processRequest(request, response);
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        request.setCharacterEncoding("UTF-8");
        response.setContentType("text/html; charset=UTF-8");
        HttpSession session = request.getSession(true);

        String action = request.getParameter("action");

        // 👑 司会者処理
        if ("createRoom".equals(action)) {
            String newGameId;
            synchronized (games) {
                do {
                    newGameId = String.format("%04d", (int)(Math.random() * 10000));
                } while (games.containsKey(newGameId));
                
                BingoGame newGame = new BingoGame(newGameId, 1);
                games.put(newGameId, newGame);
            }
            request.setAttribute("game", games.get(newGameId));
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        if ("draw".equals(action)) {
            String adminGameId = request.getParameter("gameId");
            BingoGame adminGame = games.get(adminGameId);
            if (adminGame != null) {
                List<Integer> drawn = adminGame.getDrawnNumbers();
                if (drawn.size() < 75) {
                    int nextNum;
                    do {
                        nextNum = (int)(Math.random() * 75) + 1;
                    } while (drawn.contains(nextNum));
                    drawn.add(nextNum);
                }
                request.setAttribute("game", adminGame);
            }
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        if ("reset".equals(action)) {
            String adminGameId = request.getParameter("gameId");
            BingoGame adminGame = games.get(adminGameId);
            if (adminGame != null) {
                adminGame.resetGame();
                request.setAttribute("game", adminGame);
            }
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        if ("adminView".equals(action)) {
            String adminGameId = request.getParameter("gameId");
            BingoGame adminGame = games.get(adminGameId);
            if (adminGame != null) {
                request.setAttribute("game", adminGame);
            }
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        // 👤 一般プレイヤー処理
        String targetGameId = request.getParameter("gameId");
        if (targetGameId == null || targetGameId.isEmpty()) {
            targetGameId = (String) session.getAttribute("myCurrentGameId");
        }

        if (targetGameId != null && targetGameId.length() == 4 && games.containsKey(targetGameId)) {
            session.setAttribute("myCurrentGameId", targetGameId);
        } else {
            if (!"join".equals(action)) {
                request.setAttribute("error", "⚠️ 部屋の指定が正しくないか、有効期限が切れています。");
                request.getRequestDispatcher("index.jsp").forward(request, response);
                return;
            }
        }

        BingoGame currentGame = games.get(targetGameId);
        if (currentGame == null) {
            session.removeAttribute("card");
            request.setAttribute("error", "⚠️ お探しのビンゴ部屋が見つかりませんでした。");
            request.getRequestDispatcher("index.jsp").forward(request, response);
            return;
        }

        // ⚡ 司会者がリセット（数字が0個）したら、古い記憶を完全クリア
        if (currentGame.getDrawnNumbers().isEmpty()) {
            session.removeAttribute("card");
            session.removeAttribute("myConfirmedName");
        }

        String confirmedName = (String) session.getAttribute("myConfirmedName");

        // 🚪 部屋に参加、または名前の記憶がない場合は「自動でプレイヤーX」を割り振る
        if ("join".equals(action) || confirmedName == null || confirmedName.isEmpty()) {
            if (confirmedName == null || confirmedName.isEmpty()) {
                synchronized (currentGame) {
                    // 全自動で「プレイヤー1」「プレイヤー2」...を生成
                    String uniqueName = currentGame.generateNextPlayerName();
                    // 競合を防ぐため空のカードでプレースホルダーを確保
                    currentGame.setPlayerCard(uniqueName, new ArrayList<>());
                    confirmedName = uniqueName;
                }
                session.setAttribute("myConfirmedName", confirmedName);
                session.removeAttribute("card");
            }
        }

        @SuppressWarnings("unchecked")
        List<List<String>> card = (List<List<String>>) session.getAttribute("card");
        
        if (card == null && confirmedName != null && !confirmedName.isEmpty()) {
            card = currentGame.getPlayerCard(confirmedName);
            if (card != null && card.isEmpty()) {
                card = null;
            }
            if (card != null) {
                session.setAttribute("card", card);
            }
        }
        
        if (card != null && confirmedName != null && !confirmedName.isEmpty()) {
            currentGame.setPlayerCard(confirmedName, card);
        }
        
        request.setAttribute("game", currentGame);
        request.setAttribute("confirmedPlayerName", confirmedName);
        request.setAttribute("gameId", targetGameId);

        request.getRequestDispatcher("index.jsp").forward(request, response);
    }
}
