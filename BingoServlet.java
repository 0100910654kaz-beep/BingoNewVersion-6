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

        // ⚡ 司会者がリセット（数字が0個）したら、セッション内の古いカードと名前の記憶を安全に完全クリア
        if (currentGame.getDrawnNumbers().isEmpty()) {
            session.removeAttribute("card");
            session.removeAttribute("myConfirmedName");
        }

        String confirmedName = (String) session.getAttribute("myConfirmedName");

        // 🚪【名前の全自動割り振りの中心ロジック】
        // 部屋に参加した際、またはセッションに名前の記憶がない場合は、サーバー側で「プレイヤーX」を自動確定
        if ("join".equals(action) || confirmedName == null || confirmedName.isEmpty()) {
            if (confirmedName == null || confirmedName.isEmpty()) {
                synchronized (currentGame) {
                    // BingoGame側に追加したメソッドを呼び出し、「プレイヤー1」「プレイヤー2」...を安全に生成
                    String uniqueName = currentGame.generateNextPlayerName();
                    // 同時アクセス時の競合を防ぐため、このタイミングで空のカードリストを一度共通メモリに確保
                    currentGame.setPlayerCard(uniqueName, new ArrayList<>());
                    confirmedName = uniqueName;
                }
                session.setAttribute("myConfirmedName", confirmedName);
                session.removeAttribute("card");
            }
        }

        @SuppressWarnings("unchecked")
        List<List<String>> card = (List<List<String>>) session.getAttribute("card");
        
        // Render対策：セッション切れが起きても、サーバー側の共通メモリから自動回収・復旧する
        if (card == null && confirmedName != null && !confirmedName.isEmpty()) {
            card = currentGame.getPlayerCard(confirmedName);
            // さきほどjoin内で作った「中身が空のプレースホルダーリスト」の場合は、新しく作り直させるためにnull扱いにする
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

        if (confirmedName == null) {
            confirmedName = request.getParameter("playerName");
        }
        
        request.setAttribute("game", currentGame);
        request.setAttribute("confirmedPlayerName", confirmedName);
        request.setAttribute("gameId", targetGameId);

        request.getRequestDispatcher("index.jsp").forward(request, response);
    }
}
