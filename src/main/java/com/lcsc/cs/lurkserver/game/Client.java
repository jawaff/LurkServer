package com.lcsc.cs.lurkserver.game;

import com.lcsc.cs.lurkserver.Protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Socket;
import java.util.List;

/**
 * Created by Jake on 3/29/2015.
 * This class will listen to a client's messages and will respond to it with the respective information.
 * So this class will need to have access to the game's map, player list, monsters and pretty much every part of the
 * game.
 */
public class Client extends Thread {
    private static final Logger         _logger = LoggerFactory.getLogger(Client.class);

    //The id for the client is represented by a random UUID string (if not connected yet) or the player's name!
    public               String         id = null;

    private              MailMan        _mailMan;
    private              boolean        _done;
    private              ClientState    _clientState;
    private              Game           _game;
    private              Player         _player = null;

    public Client(Socket socket, Game game) {
        _logger.debug("Just connected to " + socket.getRemoteSocketAddress());
        _done           = false;
        _game           = game;
        _clientState    = ClientState.NOT_CONNECTED;
        _mailMan        = new MailMan(socket);
        _mailMan.start();

        _mailMan.registerListener(new CommandListener() {
            @Override
            public void notify(List<Command> commands) {
                if (Client.this._clientState != ClientState.QUIT) {
                    for (Command command : commands) {
                        if (command.type == CommandType.CONNECT) {
                            if (_clientState == ClientState.NOT_CONNECTED) {
                                ResponseMessageType response = _game.clients.connectClient(command.parameter, id);

                                if (response == ResponseMessageType.NEW_PLAYER) {
                                    _clientState = ClientState.NOT_STARTED;
                                    _player = _game.players.getPlayer(id);
                                }
                                else if (response == ResponseMessageType.REPRISING_PLAYER) {
                                    _clientState = ClientState.STARTED;
                                    _player = _game.players.getPlayer(id);
                                }

                                _mailMan.sendMessage(response.getResponse());
                            }
                            else {
                                _mailMan.sendMessage(ResponseMessageType.INCORRECT_STATE.getResponse());
                            }
                        }
                        else if (command.type == CommandType.LEAVE) {
                            _game.clients.stageClientDisconnect(id);
                            Client.this._clientState = ClientState.QUIT;
                        }
                        else if (command.type == CommandType.QUERY) {
                            if (_clientState != ClientState.NOT_CONNECTED) {
                                Response response = _game.generateQueryResponse(Client.this.id);
                                _mailMan.sendMessage(response);
                            }
                            else {
                                _mailMan.sendMessage(ResponseMessageType.INCORRECT_STATE.getResponse());
                            }
                        }
                        else if (_clientState == ClientState.NOT_STARTED) {
                            Client.this.handleNotStartedState(command);
                        }
                        else if (_clientState == ClientState.STARTED) {
                            Client.this.handleStartedState(command);
                        }
                    }
                }
            }
        });
    }

    @Override
    public void run() {
        while(!_done) {
            try {
                Thread.sleep(1000);
            } catch(InterruptedException e) {
                _logger.error("Interrupted while sleeping! I'm really mad!", e);
            }
        }
    }

    public synchronized ClientState getClientState() {
        return _clientState;
    }

    /**
     * This handles the commands sent from the client while the client is in the NOT_STARTED state.
     * @param command This is the command that the client has sent.
     */
    public synchronized void handleNotStartedState(Command command) {
        if (command.type == CommandType.SET_PLAYER_DESC) {
            _player.description = command.parameter;
            _mailMan.sendMessage(ResponseMessageType.FINE.getResponse());
        }
        else {
            if (command.type == CommandType.SET_ATTACK_STAT) {
                ResponseMessageType msg = ResponseMessageType.FINE;
                try {
                    int remainingStatPoints = _player.MAX_STAT_POINTS - _player.defense - _player.regen;
                    int stat = Integer.parseInt(command.parameter);
                    if (stat >= 0 && stat <= remainingStatPoints)
                        _player.attack = stat;
                    else
                        msg = ResponseMessageType.STATS_TOO_HIGH;
                } catch(Exception e) {
                    msg = ResponseMessageType.INCORRECT_STATE;
                }
                _mailMan.sendMessage(msg.getResponse());
            }
            else if (command.type == CommandType.SET_DEFENSE_STAT) {
                ResponseMessageType msg = ResponseMessageType.FINE;
                try {
                    int remainingStatPoints = _player.MAX_STAT_POINTS - _player.attack - _player.regen;
                    int stat = Integer.parseInt(command.parameter);
                    if (stat >= 0 && stat <= remainingStatPoints)
                        _player.defense = stat;
                    else
                        msg = ResponseMessageType.STATS_TOO_HIGH;
                } catch(Exception e) {
                    msg = ResponseMessageType.INCORRECT_STATE;
                }
                _mailMan.sendMessage(msg.getResponse());
            }
            else if (command.type == CommandType.SET_REGEN_STAT) {
                ResponseMessageType msg = ResponseMessageType.FINE;
                try {
                    int remainingStatPoints = _player.MAX_STAT_POINTS - _player.attack - _player.defense;
                    int stat = Integer.parseInt(command.parameter);
                    if (stat >= 0 && stat <= remainingStatPoints)
                        _player.regen = stat;
                    else
                        msg = ResponseMessageType.STATS_TOO_HIGH;
                } catch(Exception e) {
                    msg = ResponseMessageType.INCORRECT_STATE;
                }
                _mailMan.sendMessage(msg.getResponse());
            }
            else if (command.type == CommandType.START) {
                if (_player.description == null ||
                        (_player.attack == 0 && _player.defense == 0 && _player.regen == 0)) {
                    _mailMan.sendMessage(ResponseMessageType.NOT_READY.getResponse());
                }
            }
            else {
                _mailMan.sendMessage(ResponseMessageType.INCORRECT_STATE.getResponse());
            }
        }
    }

    public synchronized void handleStartedState(Command command) {

    }

    public synchronized void dropClient() {
        _done = true;
        _logger.debug("Dropping client!");
        _mailMan.disconnect();
        try {
            this._mailMan.join();
            _logger.debug("Joined MailMan thread!");
        } catch (InterruptedException e) {
            _logger.error("Interrupted when joining the MailMan's thread", e);
        }
    }
}