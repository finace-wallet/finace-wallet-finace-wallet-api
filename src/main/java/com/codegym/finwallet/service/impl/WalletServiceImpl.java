package com.codegym.finwallet.service.impl;

import com.codegym.finwallet.constant.WalletConstant;
import com.codegym.finwallet.dto.CommonResponse;
import com.codegym.finwallet.dto.payload.request.TransferMoneyRequest;
import com.codegym.finwallet.dto.payload.request.WalletRequest;
import com.codegym.finwallet.entity.AppUser;
import com.codegym.finwallet.entity.Wallet;
import com.codegym.finwallet.repository.AppUserRepository;
import com.codegym.finwallet.repository.WalletRepository;
import com.codegym.finwallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {
    private final WalletRepository walletRepository;
    private final ModelMapper modelMapper;
    private final AppUserRepository appUserRepository;

    @Override
    public Page<Wallet> findAllByEmail(Pageable pageable) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return walletRepository.findAllByEmail(pageable,email);
    }

    @Override
    public CommonResponse createWallet(WalletRequest request) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();
            AppUser appUser = appUserRepository.findByEmail(email);
            Wallet wallet = modelMapper.map(request,Wallet.class);
            wallet.setUsers(Collections.singletonList(appUser));
            appUserRepository.findByEmail(email);
            walletRepository.save(wallet);
            return CommonResponse.builder()
                    .data(wallet)
                    .message(WalletConstant.CREATE_NEW_WALLET_SUCCESS_MESSAGE)
                    .status(HttpStatus.CREATED)
                    .build();
        } catch (AuthenticationException e){
            return CommonResponse.builder()
                    .data(null)
                    .message(e.getMessage())
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }
    }


    @Override
    public CommonResponse deleteWallet(Long id) {
        try {
            Optional<Wallet> walletOptional = walletRepository.findById(id);
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();
            if (walletOptional.isPresent() && isUserWallet(id,email)) {
                Wallet wallet = walletOptional.get();
                wallet.setDelete(true);
                walletRepository.save(wallet);
                return CommonResponse.builder()
                        .data(null)
                        .message(WalletConstant.UPDATE_WALLET_INFORMATION_SUCCESS_MESSAGE)
                        .status(HttpStatus.OK)
                        .build();
        }
        }catch (SecurityException e){
            return CommonResponse.builder()
                    .data(null)
                    .message(WalletConstant.UPDATE_WALLET_INFORMATION_FAILURE_MESSAGE)
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }
        return CommonResponse.builder()
                .data(null)
                .message(WalletConstant.UPDATE_WALLET_INFORMATION_DENIED)
                .status(HttpStatus.UNAUTHORIZED)
                .build();
    }

    @Override
    public Wallet findById(Long id) {
        Optional<Wallet> wallet = walletRepository.findById(id);
        Wallet newWallet = new Wallet();
        if (wallet.isPresent()){
             newWallet = wallet.get();
        }
        return newWallet;
    }

    @Override
    public CommonResponse editWallet(WalletRequest walletRequest,Long id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        try {
            Optional<Wallet> walletOptional = walletRepository.findById(id);
            if (!walletOptional.isPresent()) {
                return CommonResponse.builder()
                        .data(null)
                        .message(WalletConstant.WALLET_NOT_FOUND_MESSAGE)
                        .status(HttpStatus.NOT_FOUND)
                        .build();
            }
            if (isUserWallet(id, email)) {
                Wallet wallet = walletOptional.get();
                float currentAmount = wallet.getAmount();
                float inputAmount = walletRequest.getAmount();
                if (inputAmount < 0) {
                    return CommonResponse.builder()
                            .data(null)
                            .message(WalletConstant.AMOUNT_NOT_AVAILABLE)
                            .status(HttpStatus.BAD_REQUEST)
                            .build();
                }
                float newAmount = currentAmount + inputAmount;
                wallet.setAmount(newAmount);
                wallet.setIcon(walletRequest.getIcon());
                wallet.setName(walletRequest.getName());
                wallet.setDescription(walletRequest.getDescription());
                walletRepository.save(wallet);
                return CommonResponse.builder()
                        .data(wallet)
                        .message(WalletConstant.UPDATE_WALLET_INFORMATION_SUCCESS_MESSAGE)
                        .status(HttpStatus.OK)
                        .build();
            } else {
                return CommonResponse.builder()
                        .data(null)
                        .message(WalletConstant.UPDATE_WALLET_INFORMATION_DENIED)
                        .status(HttpStatus.FORBIDDEN)
                        .build();
            }
        } catch (SecurityException e) {
            return CommonResponse.builder()
                    .data(null)
                    .message(WalletConstant.UPDATE_WALLET_INFORMATION_FAILURE_MESSAGE)
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }
    }

    private boolean isUserWallet(Long walletId, String email){
        List<Wallet> wallets = walletRepository.findWalletByEmail(email);
        Optional<Wallet> walletOptional = wallets.stream().filter(w -> w.getId().equals(walletId))
                .findFirst();
        return walletOptional.isPresent();
        }

    @Override
    public CommonResponse transferMoney(TransferMoneyRequest transferMoneyRequest) {
        String sourceEmail = getAuthenticatedEmail();
        Wallet sourceWallet = getWalletByEmailAndId(sourceEmail, transferMoneyRequest.getSourceWalletId());
        Wallet destinationWallet = getWalletByEmailAndId(transferMoneyRequest.getDestinationEmail(), transferMoneyRequest.getDestinationWalletId());

        if (sourceWallet != null && destinationWallet != null) {
            return processTransfer(sourceWallet, destinationWallet, transferMoneyRequest.getAmount());
        } else {
            return buildResponse(null, WalletConstant.WALLET_NOT_FOUND_MESSAGE, HttpStatus.NOT_FOUND);
        }
    }

    private String getAuthenticatedEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }

    private Wallet getWalletByEmailAndId(String email, Long walletId) {
        List<Wallet> wallets = walletRepository.findWalletByEmail(email);
        Optional<Wallet> walletOptional = wallets.stream().filter(wallet -> wallet.getId().equals(walletId)).findFirst();
        return walletOptional.orElse(null);
    }

    private CommonResponse processTransfer(Wallet sourceWallet, Wallet destinationWallet, float amount) {
        if (sourceWallet.getAmount() >= amount) {
            updateWalletAmounts(sourceWallet, destinationWallet, amount);
            return buildResponse(null, WalletConstant.SUCCESSFUL_MONEY_TRANSFER, HttpStatus.OK);
        } else {
            return buildResponse(null, WalletConstant.INSUFFICIENT_ACCOUNT_BALANCE, HttpStatus.BAD_REQUEST);
        }
    }

    private void updateWalletAmounts(Wallet sourceWallet, Wallet destinationWallet, float amount) {
        sourceWallet.setAmount(sourceWallet.getAmount() - amount);
        destinationWallet.setAmount(destinationWallet.getAmount() + amount);
        walletRepository.save(sourceWallet);
        walletRepository.save(destinationWallet);
    }

    private CommonResponse buildResponse(Object data, String message, HttpStatus status) {
        return CommonResponse.builder()
                .data(data)
                .message(message)
                .status(status)
                .build();
    }


    @Override
    public CommonResponse addMoneyToWallet(Long walletId, float amount) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = authentication.getName();
        List<Wallet> wallets = walletRepository.findWalletByEmail(userEmail);
        Optional<Wallet> walletOptional = wallets.stream().filter(wallet -> wallet.getId().equals(walletId)).findFirst();
        if (walletOptional.isPresent()) {
            Wallet wallet = walletOptional.get();
            float currentAmount = wallet.getAmount();
            wallet.setAmount(currentAmount + amount);
            walletRepository.save(wallet);
            return CommonResponse.builder()
                    .data(null)
                    .message("Money added to wallet successfully.")
                    .status(HttpStatus.OK)
                    .build();
        } else {
            return CommonResponse.builder()
                    .data(null)
                    .message("Wallet not found.")
                    .status(HttpStatus.NOT_FOUND)
                    .build();
        }
    }
}
