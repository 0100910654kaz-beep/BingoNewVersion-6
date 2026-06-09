<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="servlet.BingoGame" %>
<%@ page import="servlet.PlayerResult" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%
    BingoGame game = (BingoGame) request.getAttribute("game");
    String gameId = (game != null) ? game.getGameId() : "";

    List<Integer> reverseDrawnNumbers = new ArrayList<>();
    int ballCount = 0;
    if (game != null) {
        reverseDrawnNumbers.addAll(game.getDrawnNumbers());
        ballCount = reverseDrawnNumbers.size();
        Collections.reverse(reverseDrawnNumbers);
    }
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>ビンゴ大会 - 司会者画面</title>
    <style>
        body { font-family: Arial, sans-serif; background-color: #eef2f3; padding: 10px; text-align: center; margin: 0; }
        .admin-container { max-width: 800px; margin: 10px auto; background: white; padding: 20px; border-radius: 12px; box-shadow: 0 4px 15px rgba(0,0,0,0.1); box-sizing: border-box; }
        h1 { color: #2b3a42; font-size: 22px; margin-top: 5px; margin-bottom: 20px; }
        .info-panel { background: #f8f9fa; padding: 15px; border-radius: 8px; margin-bottom: 20px; font-size: 16px; border: 1px solid #dee2e6; font-weight: bold; }
        .grid-container { display: grid; grid-template-columns: 1fr; gap: 15px; text-align: left; }
        @media(min-width: 600px) { .grid-container { grid-template-columns: 1fr 1fr; } }
        .panel { background: #fff; padding: 15px; border-radius: 8px; border: 1px solid #dee2e6; box-sizing: border-box; }
        h3 { margin-top: 0; color: #495057; border-bottom: 2px solid #dee2e6; padding-bottom: 5px; font-size: 15px; }
        .btn { padding: 12px 20px; font-size: 16px; font-weight: bold; border: none; border-radius: 6px; cursor: pointer; margin: 5px; display: inline-block; }
        .btn-draw { background-color: #2b8a3e; color: white; }
        .btn-draw:hover { background-color: #216a2f; }
        .btn-reset { background-color: #e63946; color: white; }
        .btn-reset:hover { background-color: #b11e29; }
        .number-display { font-size: 44px; font-weight: bold; color: #e63946; margin: 15px 0; min-height: 54px; text-align: center; }
        .history-list { display: flex; flex-wrap: wrap; gap: 6px; list-style: none; padding: 0; justify-content: center; }
        .history-item { width: 35px; height: 35px; background: #e9ecef; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 14px; color: #495057; }
        .history-item.first { background: #ffc9c9; color: #c92a2a; border: 2px solid #c92a2a; animation: pulse 1s infinite alternate; }
        @keyframes pulse { from { transform: scale(1); } to { transform: scale(1.1); } }
        ul { padding-left: 20px; margin: 0; }
        li { margin-bottom: 8px; font-size: 14px; }
    </style>
    <script>
        // 5秒ごとに自動リロードして参加者状況やリーチ・ビンゴを同期するタイマー
        setInterval(function() {
            var daysInput = document.getElementById("validDaysInput");
            if (daysInput) {
                // まだ部屋を作成していない状態の時は自動リロードを行いません
                return;
            }

            // 部屋番号が確定している場合、サーブレットのadminView（管理画面再表示）を呼び出す
            var currentGameId = "<%= gameId %>";
            if (currentGameId && currentGameId !== "") {
                window.location.href = "BingoServlet?action=adminView&gameId=" + currentGameId;
            }
        }, 5000);
    </script>
</head>
<body>

<div class="admin-container">
    <h1>🎤 ビンゴ大会 司会者コントロール 🎤</h1>

    <div class="info-panel">
        部屋番号 (ゲームID): <span style="font-size: 24px; font-weight: bold; color: #e63946;"><%= (gameId.isEmpty()) ? "まだ開始していません" : gameId %></span>
        <% if (game != null) { %>
            <span style="font-size: 14px; color: #6c757d; margin-left: 15px; display:block;">(現在のユニーク参加カード数: <%= game.getPlayerCount() %> 枚)</span>
        <% } %>
    </div>

    <%-- 🛡️ 部屋未作成時のフォーム。サーブレットの「createRoom」・「POST方式」へ完全同期 --%>
    <% if (game == null || gameId.isEmpty()) { %>
        <div class="panel" style="text-align: center; margin-bottom: 20px; background: #fff5f5;">
            <p style="font-weight: bold; color: #c92a2a; margin-top: 0;">ビンゴゲームの部屋がまだ作成されていません。</p>
            <form action="BingoServlet" method="post">
                <input type="hidden" name="action" value="createRoom">
                <label style="font-weight: bold;">部屋の有効日数: </label>
                <input type="number" id="validDaysInput" name="validDays" value="1" style="width:60px; padding:6px; text-align:center; font-size:16px; border-radius:4px; border:1px solid #ccc;" min="1" required> 日間
                <br><br>
                <button type="submit" class="btn btn-draw" style="background:#228be6;">新規に部屋を作成する</button>
            </form>
        </div>
    <% } %>

    <%-- 👑 部屋が作成されている場合の管理コントロールパネル --%>
    <% if (game != null && !gameId.isEmpty()) { %>
        <div style="margin-bottom: 20px;">
            <form action="BingoServlet" method="post" style="display:inline;">
                <input type="hidden" name="action" value="draw">
                <input type="hidden" name="gameId" value="<%= gameId %>">
                <button type="submit" class="btn btn-draw">🔮 玉を1個引く</button>
            </form>

            <form action="BingoServlet" method="post" style="display:inline;" onsubmit="return confirm('本当にゲームをリセットしますか？出た数字や全員のカードが初期化されます。');">
                <input type="hidden" name="action" value="reset">
                <input type="hidden" name="gameId" value="<%= gameId %>">
                <button type="submit" class="btn btn-reset">🔄 ゲームをリセット</button>
            </form>
        </div>

        <div class="panel" style="margin-bottom: 20px;">
            <h3>📢 当選番号のコール</h3>
            <div class="number-display">
                <%= (game.getDrawnNumbers().isEmpty()) ? "⏳ スタートを待っています" : game.getDrawnNumbers().get(game.getDrawnNumbers().size() - 1) + " 番" %>
            </div>
            <div style="font-size: 13px; color: #6c757d; text-align: right; margin-bottom: 8px;">
                これまでに引いた玉の数: <%= ballCount %> 個 / 75 個
            </div>
            <ul class="history-list">
                <% for (int i = 0; i < reverseDrawnNumbers.size(); i++) { 
                    int num = reverseDrawnNumbers.get(i);
                    if (i == 0) { %>
                        <li class="history-item first"><%= num %></li>
                    <% } else { %>
                        <li class="history-item"><%= num %></li>
                    <% }
                } %>
            </ul>
        </div>

        <div class="grid-container">
            <div class="panel">
                <h3>👥 参加中の名簿 (<%= game.getAllPlayers().size() %>人)</h3>
                <div style="max-height: 250px; overflow-y: auto;">
                    <ul>
                        <% for (String name : game.getAllPlayers()) { %>
                            <li>• <%= name %></li>
                        <% } 
                           if (game.getAllPlayers().isEmpty()) { %> <p style="color:#888; font-size:13px;">まだ誰も参加していません</p> <% } %>
                    </ul>
                </div>
            </div>

            <div class="panel">
                <h3>🏆 ビンゴ達成者一覧</h3>
                <ul id="bingoList">
                    <% 
                       List<PlayerResult> bingoList = game.getBingoPlayers();
                       int totalCount = bingoList.size();
                       
                       for (int i = 0; i < totalCount; i++) {
                           PlayerResult p = bingoList.get(i);
                           int currentRank = totalCount - i; 
                    %>
                        <li><strong><%= currentRank %>位</strong>: <%= p.getPlayerName() %> さん <span style="color:#e63946; font-weight:bold;">(🔑<%= p.getDrawnNumberAtBingo() %>番でビンゴ!)</span></li>
                    <% 
                       } 
                       if (bingoList.isEmpty()) { %> <p style="color:#888; font-size:13px;">まだビンゴした人はいません</p> <% } 
                    %>
                </ul>

                <h3 style="margin-top: 25px;">🔥 リーチの人（全自動検知）</h3>
                <ul>
                    <% for (PlayerResult p : game.getReachPlayers()) { %>
                        <li><strong><%= p.getPlayerName() %> さん</strong> <span style="color: #ff9800; font-size: 14px; font-weight: bold;">（あと <%= game.getWaitNumbers(p.getPlayerName()) %> 番でビンゴ！）</span></li>
                    <% } 
                       if (game.getReachPlayers().isEmpty()) { %> <p style="color:#888; font-size:13px;">まだリーチの人はいません</p> <% } %>
                </ul>
            </div>
        </div>
    <% } %>
</div>

</body>
</html>
