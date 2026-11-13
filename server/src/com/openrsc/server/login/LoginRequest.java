package com.openrsc.server.login;

import com.openrsc.server.Server;
import com.openrsc.server.database.GameDatabaseException;
import com.openrsc.server.database.impl.mysql.queries.logging.LoginLog;
import com.openrsc.server.database.struct.InvoluntaryChangeDetails;
import com.openrsc.server.database.struct.PlayerLoginData;
import com.openrsc.server.database.struct.UsernameChangeType;
import com.openrsc.server.event.rsc.ImmediateEvent;
import com.openrsc.server.model.entity.player.Group;
import com.openrsc.server.content.worldedit.WorldBuilderMode;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.LoginResponse;
import com.openrsc.server.util.rsc.RegisterLoginResponse;
import io.netty.channel.Channel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;

public abstract class LoginRequest extends LoginExecutorProcess{
	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	private final Server server;
	private final Channel channel;
	protected Player loadedPlayer;
	private String ipAddress;
	private String username;
	private String password;
	private long usernameHash;
	private int clientVersion;
	private boolean authenticClient;
	private boolean reconnecting;
	private boolean isSimLogin;
	private int[] nonces;
	private UsernameChangeType usernameChangeType = UsernameChangeType.NOT_RENAMED;
    private volatile LoginAdmissionReservations.Lease admission;
    private int authenticatedAccountId;
    private String authenticatedHash,authenticatedSalt;
    protected boolean stagedLoginRequested(){return false;}
    public void configureStagedPlayer(Player candidate){ }
    public LoginAdmissionReservations.Lease getAdmission(){return admission;}
    public final void releaseAdmission(){LoginAdmissionReservations.Lease lease=admission;if(lease!=null)lease.release();}
    public final void clearLoginSecret(){password=null;}
    public Player reloadStagedPlayer() {
        try {
            PlayerLoginData current=server.getDatabase().getPlayerLoginData(getUsername());
            if(current==null||current.id!=authenticatedAccountId||!java.util.Objects.equals(current.password,authenticatedHash)
                ||!java.util.Objects.equals(current.salt,authenticatedSalt)||current.banned==-1||current.banned>System.currentTimeMillis()
                ||server.isRestarting()||server.isShuttingDown()||!getChannel().isActive()
                ||!admission.isLive(System.currentTimeMillis())||server.getWorld().getPlayerID(authenticatedAccountId)!=null)return null;
            Player candidate=server.getPlayerService().loadPlayer(this);
            if(candidate!=null)configureStagedPlayer(candidate);
            return candidate;
        }catch(GameDatabaseException failure){LOGGER.error("Unable to revalidate prepared login",failure);return null;}
    }
    public final boolean finishAdmission(final Player candidate,final Runnable beforeCommit) {
        if(admission==null||!getChannel().isActive())return false;
        try {
            return admission.commit(System.currentTimeMillis(),()->{
                if(!getChannel().isActive()||!admission.isLive(System.currentTimeMillis())
                    ||server.isRestarting()||server.isShuttingDown()||server.getWorld().getPlayerID(candidate.getDatabaseID())!=null)
                    throw new IllegalStateException("Login admission changed");
                if(server.getWorld().getPlayers().size()>=server.getConfig().MAX_PLAYERS&&!candidate.isAdmin())
                    throw new IllegalStateException("World filled during preparation");
                beforeCommit.run();
                if(!getChannel().isActive())throw new IllegalStateException("Connection closed before admission");
                candidate.setLoggedIn(true);loadingComplete(candidate);
                if(server.getWorld().getPlayerID(candidate.getDatabaseID())!=candidate)throw new IllegalStateException("Player was not admitted");
                if(!getChannel().isActive())candidate.unregister(com.openrsc.server.model.entity.UnregisterForcefulness.WAIT_UNTIL_COMBAT_ENDS,"Login connection closed");
                candidate.desertHeatInit();ActionSender.sendReleasedNameExplanation(candidate,usernameChangeType);
            },()->server.getWorld().getPlayerID(candidate.getDatabaseID())==candidate);
        }catch(RuntimeException|Error failure){
            LOGGER.error("Login admission failed",failure);
            if(server.getWorld().getPlayerID(candidate.getDatabaseID())!=candidate){
                if(candidate.getDrainer()!=null){candidate.getDrainer().stop();server.getGameEventHandler().remove(candidate.getDrainer());}
                candidate.getStatRestorationEvent().stop();server.getGameEventHandler().remove(candidate.getStatRestorationEvent());
                candidate.setLoggedIn(false);candidate.closePersistenceSession();
                com.openrsc.server.net.ConnectionAttachment att=getChannel().attr(com.openrsc.server.net.RSCConnectionHandler.attachment).get();
                if(att!=null)att.player.compareAndSet(candidate,null);
            }
            getChannel().close();return false;
        }
    }


	protected LoginRequest(final Server server, final Channel channel, final String username, final String password, final boolean isAuthenticClient, final int clientVersion, final boolean reconnecting, final int[] nonces) {
		this.server = server;
		this.channel = channel;
		this.setUsername(DataConversions.sanitizeUsername(username));
		this.setPassword(password);
		this.setAuthenticClient(isAuthenticClient);
		this.setIpAddress(((InetSocketAddress) channel.remoteAddress()).getAddress().getHostAddress());
		this.setClientVersion(clientVersion);
		this.setUsernameHash(DataConversions.usernameToHash(username));
		this.reconnecting = reconnecting;
		this.isSimLogin = false;
		this.nonces = nonces;
	}

	// only used for sim login
	protected LoginRequest(final Server server, final String username, final String ip, final int clientVersion) {
		this.server = server;
		this.channel = null;
		this.setUsername(DataConversions.sanitizeUsername(username));
		this.setAuthenticClient(clientVersion <= 235);
		this.setIpAddress(ip);
		this.setClientVersion(clientVersion);
		this.setUsernameHash(DataConversions.usernameToHash(username));
		this.reconnecting = false;
		this.isSimLogin = true;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	private void setIpAddress(final String ipAddress) {
		this.ipAddress = ipAddress;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(final String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	private void setPassword(final String password) {
		this.password = password;
	}

	public long getUsernameHash() {
		return usernameHash;
	}

	private void setUsernameHash(final long usernameHash) {
		this.usernameHash = usernameHash;
	}

	public boolean getAuthenticClient() {
		return authenticClient;
	}

	private void setAuthenticClient(final boolean authenticClient) {
		this.authenticClient = authenticClient;
	}

	public Channel getChannel() {
		return channel;
	}

	public Server getServer() {
		return server;
	}

	public int getClientVersion() {
		return clientVersion;
	}

	private void setClientVersion(final int clientVersion) {
		this.clientVersion = clientVersion;
	}

	public abstract void loginValidated(int response);

	public abstract void loadingComplete(Player loadedPlayer);

    protected void processInternal() {
        boolean queued=false;
        try {
            ValidatedLogin vl=validateLogin();int response=vl.responseCode;Player candidate=null;
            if(!isSimLogin&&isValidatedLoginSuccessful(response)){
                candidate=getServer().getPlayerService().loadPlayer(this);
                if(candidate==null)response=LoginResponse.LOGINSERVER_OFFLINE;
            }
            if(candidate!=null&&stagedLoginRequested()){
                clearLoginSecret();final Player loaded=candidate;final int success=response;
                getServer().getGameEventHandler().add(new ImmediateEvent(server.getWorld(),"Prepare Browser Login"){
                    public void action(){
                        if(!getChannel().isActive()||admission==null||!admission.isLive(System.currentTimeMillis())){releaseAdmission();getChannel().close();return;}
                        configureStagedPlayer(loaded);StagedLoginSession.begin(LoginRequest.this,loaded,success);
                    }
                });queued=true;return;
            }
            if(clientVersion<=204)response=RegisterLoginResponse.translateNewToOld(response,clientVersion,false);
            loginValidated(response);
            if(candidate!=null){
                final Player loaded=candidate;
                getServer().getGameEventHandler().add(new ImmediateEvent(server.getWorld(),"Login Player"){
                    public void action(){if(!finishAdmission(loaded,()->{})){releaseAdmission();getChannel().close();}}
                });queued=true;
            }
        }finally{clearLoginSecret();if(!queued)releaseAdmission();}
    }

	public ValidatedLogin validateLogin() {
        // Wire compatibility is mandatory, including administrators and disabled version enforcement.
        if (!com.openrsc.server.net.rsc.NpcCapacityContract.supportsWorld(
                getClientVersion(), getServer().getConfig().CLIENT_VERSION, getServer().getConfig().NPC_CAPACITY)) {
            return new ValidatedLogin(LoginResponse.CLIENT_UPDATED);
        }
		PlayerLoginData playerData;
		int groupId = Group.USER;
		try {
			if (getServer().isRestarting() || getServer().isShuttingDown() || !getServer().getLoginExecutor().isRunning()) {
				return new ValidatedLogin(LoginResponse.WORLD_DOES_NOT_ACCEPT_NEW_PLAYERS);
			}

			if (getServer().getConfig().WORLD_BUILDER_MODE
				&& !WorldBuilderMode.isBuilderAccount(username)) {
				return new ValidatedLogin(LoginResponse.INVALID_CREDENTIALS);
			}

			if(!getServer().getPacketFilter().shouldAllowLogin(getIpAddress(), false)) {
				return new ValidatedLogin(LoginResponse.LOGIN_ATTEMPTS_EXCEEDED);
			}

			playerData = getServer().getDatabase().getPlayerLoginData(username);

			if (playerData == null) {
				// didn't find player that exists, try again by former name
				InvoluntaryChangeDetails oldUsername = getServer().getDatabase().queryFormerNameInvoluntaryChange(username);
				if (null != oldUsername) {
					username = oldUsername.username;
					playerData = getServer().getDatabase().getPlayerLoginData(username);
					usernameChangeType = oldUsername.changeType;
				}
			} else {
				// check for released name that has been reassigned
				// (password will almost always mismatch, else they've logged into the new account)
				if (!DataConversions.checkPassword(getPassword(), playerData.salt, playerData.password)) {
					// TODO: could support searching more than just one formerPlayerData if more than one person has the same released name
					// wouldn't be an issue until years from now.
					PlayerLoginData formerPlayerData = getServer().getDatabase().getPlayerLoginDataByFormerName("$" + username);
					if (null != formerPlayerData && DataConversions.checkPassword(getPassword(), formerPlayerData.salt, formerPlayerData.password)) {
						// password matches for a player who used to be named this, and had their account name freed without their knowledge.
						usernameChangeType = UsernameChangeType.RELEASED;
						username = formerPlayerData.username;
						playerData = formerPlayerData;
					}
				}
			}

			boolean isAdmin = getServer().getPacketFilter().isHostAdmin(getIpAddress());
			if (playerData != null) {
				groupId = playerData.groupId;
				isAdmin = isAdmin || groupId == Group.OWNER || groupId == Group.ADMIN;
			}

			if(!getIpAddress().equals("127.0.0.1") && getServer().getPacketFilter().getPasswordAttemptsCount(getIpAddress()) >= getServer().getConfig().MAX_PASSWORD_GUESSES_PER_FIVE_MINUTES && !isAdmin) {
				return new ValidatedLogin(LoginResponse.LOGIN_ATTEMPTS_EXCEEDED);
			}

			if (getServer().getPacketFilter().isHostIpBanned(getIpAddress()) && !isAdmin) {
				LOGGER.debug(getIpAddress() + " denied for being host ip banned...!");
				return new ValidatedLogin(LoginResponse.ACCOUNT_TEMP_DISABLED);
			}


			if (getClientVersion() != getServer().getConfig().CLIENT_VERSION && !isAdmin) {
				if (getClientVersion() > 10000) {
					if (getServer().getConfig().ENFORCE_CUSTOM_CLIENT_VERSION) {
						return new ValidatedLogin(LoginResponse.CLIENT_UPDATED);
					}
				} else {
					if (getServer().getConfig().WANT_CUSTOM_SPRITES) {
						return new ValidatedLogin(LoginResponse.CLIENT_UPDATED);
					}
				}
			}

			final long i = getServer().getTimeUntilShutdown();
			if (i > 0 && i < 30000 && !isAdmin) {
				return new ValidatedLogin(LoginResponse.WORLD_DOES_NOT_ACCEPT_NEW_PLAYERS);
			}

			if (playerData == null) {
				server.getPacketFilter().addPasswordAttempt(getIpAddress());
				return new ValidatedLogin(LoginResponse.INVALID_CREDENTIALS);
			}

			if(getServer().getWorld().getPlayers().size() >= getServer().getConfig().MAX_PLAYERS && !isAdmin) {
					return new ValidatedLogin(LoginResponse.WORLD_IS_FULL);
			}

			if (getServer().getWorld().getPlayer(getUsernameHash()) != null) {
				return new ValidatedLogin(LoginResponse.ACCOUNT_LOGGEDIN);
			}

			int playersCount = getServer().getPacketFilter().getPlayersCount(getIpAddress());
			if (!isAdmin && !getIpAddress().equals("127.0.0.1") && playersCount >= getServer().getConfig().MAX_PLAYERS_PER_IP) {
				LOGGER.info(getIpAddress() + " is using " + playersCount + " out of " + getServer().getConfig().MAX_PLAYERS_PER_IP + " allowed sessions, rejecting additional connection.");
				return new ValidatedLogin(LoginResponse.IP_IN_USE);
			}

			final long banExpires = playerData.banned;
			if (banExpires == -1) {
				return new ValidatedLogin(LoginResponse.ACCOUNT_PERM_DISABLED);
			}

			final double timeBanLeft = (double) (banExpires - System.currentTimeMillis());
			if (timeBanLeft >= 1) {
				LOGGER.debug(getIpAddress() + " denied for being *actually* temp banned...!");
				return new ValidatedLogin(LoginResponse.ACCOUNT_TEMP_DISABLED);
			}

			if (!isSimLogin && !DataConversions.checkPassword(getPassword(), playerData.salt, playerData.password)) {
				server.getPacketFilter().addPasswordAttempt(getIpAddress());
				return new ValidatedLogin(LoginResponse.INVALID_CREDENTIALS);
			}

			// all other checks passed, check cryptographic nonces have not been used before by inserting into a UNIQUE column
			if (!isSimLogin && !getServer().getDatabase().queryInsertLoginAttempt(new LoginLog(playerData.id, getIpAddress(), clientVersion, nonces))) {
				return new ValidatedLogin(LoginResponse.INVALID_CREDENTIALS);
			}

			// Doing this at end because we only want to flag the host as an admin _IF_ they know the password.
			if(isAdmin) {
				getServer().getPacketFilter().addAdminHost(getIpAddress());
			}


		} catch (GameDatabaseException e) {
			LOGGER.error("Database Exception during validateLogin", e);
			return new ValidatedLogin(LoginResponse.LOGIN_UNSUCCESSFUL);
		}

        if(!isSimLogin){
            if(server.getWorld().getPlayerID(playerData.id)!=null)return new ValidatedLogin(LoginResponse.ACCOUNT_LOGGEDIN);
            authenticatedAccountId=playerData.id;authenticatedHash=playerData.password;authenticatedSalt=playerData.salt;
            final boolean track=!(reconnecting&&clientVersion<=204);
            admission=server.getLoginAdmissions().acquire(playerData.id,getIpAddress(),getChannel(),System.currentTimeMillis(),stagedLoginRequested(),
                Math.max(1,server.getConfig().MAX_PLAYERS_PER_IP),
                ()->{if(track)server.getPacketFilter().addLoggedInPlayer(getIpAddress(),getUsernameHash());},
                ()->{if(track)server.getPacketFilter().removeLoggedInPlayer(getIpAddress(),getUsernameHash());});
            if(admission==null)return new ValidatedLogin(LoginResponse.ACCOUNT_LOGGEDIN);
            final LoginAdmissionReservations.Lease lease=admission;
            getChannel().closeFuture().addListener(ignored->lease.release());
            lease.expirationTask(getChannel().eventLoop().schedule(()->{if(lease.expire(System.currentTimeMillis()))getChannel().close();},
                LoginAdmissionReservations.TTL_MILLIS,java.util.concurrent.TimeUnit.MILLISECONDS));
        }
        if(reconnecting&&clientVersion<=204)return new ValidatedLogin(LoginResponse.RECONNECT_SUCCESFUL);
		return new ValidatedLogin(LoginResponse.LOGIN_SUCCESSFUL[groupId]);
	}

    /** validateLogin returns modern response codes; legacy wire codes must never admit a load. */
    private static boolean isValidatedLoginSuccessful(int response) {
        if (response == LoginResponse.RECONNECT_SUCCESFUL) return true;
        for (int success : LoginResponse.LOGIN_SUCCESSFUL) if (response == success) return true;
        return false;
    }

	public boolean isLoginSuccessful(int loginResponse) {
		return (loginResponse & 0x40) != LoginResponse.LOGIN_UNSUCCESSFUL ||
			((loginResponse == 0 || loginResponse == 1) && clientVersion <= 204);
	}
}
